#!/usr/bin/env bash
set -Eeuo pipefail

backup_root="${BACKUP_ROOT:-/backups}"
model_root="${MODEL_STORAGE_PATH:-/data/models}"
backup_id="${BACKUP_ID:-latest}"

[[ "${RESTORE_CONFIRM:-}" == "RESTORE" ]] || {
  echo "Set RESTORE_CONFIRM=RESTORE to run recovery" >&2
  exit 2
}
: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_USER:?MYSQL_USER is required}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"
case "$model_root" in
  /data/models|/restore/models) ;;
  *) echo "MODEL_STORAGE_PATH must be /data/models or /restore/models" >&2; exit 2 ;;
esac

if [[ "$backup_id" == "latest" ]]; then
  backup_id="$(find "$backup_root" -mindepth 1 -maxdepth 1 -type d \
    -name '????????T??????Z' -printf '%f\n' | sort -r | head -n 1)"
fi
[[ "$backup_id" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || { echo "Invalid BACKUP_ID" >&2; exit 2; }
source_dir="$backup_root/$backup_id"
[[ -d "$source_dir" ]] || { echo "Backup not found: $backup_id" >&2; exit 2; }

(
  cd "$source_dir"
  sha256sum --check SHA256SUMS
)

export MYSQL_PWD="$MYSQL_PASSWORD"
for attempt in $(seq 1 60); do
  if mysqladmin ping -h "$MYSQL_HOST" -P "${MYSQL_PORT:-3306}" -u "$MYSQL_USER" --protocol=tcp --silent; then
    break
  fi
  if (( attempt == 60 )); then
    echo "MySQL did not become ready for restore" >&2
    exit 1
  fi
  sleep 2
done
for database in enterprise_core enterprise_workload enterprise_ml; do
  gzip -dc "$source_dir/${database}.sql.gz" | \
    mysql -h "$MYSQL_HOST" -P "${MYSQL_PORT:-3306}" -u "$MYSQL_USER"
done

mkdir -p "$model_root"
find "$model_root" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
tar -C "$model_root" -xzf "$source_dir/model-artifacts.tar.gz"

while IFS=$'\t' read -r _ version _ artifact_path _ _ _; do
  [[ -z "$version" ]] && continue
  artifact_name="${artifact_path#/data/models/}"
  [[ "$artifact_name" != /* && "$artifact_name" != *..* ]] || { echo "Unsafe artifact path" >&2; exit 2; }
  [[ -f "$model_root/$artifact_name" ]] || {
    echo "Model artifact is missing after restore: $version" >&2
    exit 1
  }
done < "$source_dir/model-registry.tsv"

# V2 artifacts live in subdirectories; verify all candidate/archived versions too.
mysql -h "$MYSQL_HOST" -P "${MYSQL_PORT:-3306}" -u "$MYSQL_USER" --batch --raw --skip-column-names enterprise_ml \
  -e "SELECT file_path,file_sha256 FROM ml_v2_model_versions" > /tmp/v2-model-checks.tsv
while IFS=$'\t' read -r artifact_path expected_sha; do
  [[ -z "$artifact_path" ]] && continue
  relative="${artifact_path#/data/models/}"
  [[ "$relative" != /* && "$relative" != *..* ]] || { echo "Unsafe V2 artifact path" >&2; exit 2; }
  [[ "$expected_sha" =~ ^[a-f0-9]{64}$ ]] || exit 2
  printf '%s  %s\n' "$expected_sha" "$model_root/$relative" | sha256sum --check
done < /tmp/v2-model-checks.tsv

echo "Restore completed and verified: $backup_id"
