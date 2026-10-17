#!/usr/bin/env bash
set -Eeuo pipefail

backup_root="${BACKUP_ROOT:-/backups}"
model_root="${MODEL_STORAGE_PATH:-/data/models}"
retention="${BACKUP_RETENTION_COUNT:-7}"
interval="${BACKUP_INTERVAL_SECONDS:-86400}"

[[ "$retention" =~ ^[0-9]+$ ]] && (( retention >= 7 )) || {
  echo "BACKUP_RETENTION_COUNT must be an integer greater than or equal to 7" >&2
  exit 2
}
[[ "$interval" =~ ^[0-9]+$ ]] && (( interval >= 60 )) || {
  echo "BACKUP_INTERVAL_SECONDS must be an integer greater than or equal to 60" >&2
  exit 2
}
: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_USER:?MYSQL_USER is required}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"

export MYSQL_PWD="$MYSQL_PASSWORD"
umask 077

wait_for_mysql() {
  local attempt
  for attempt in $(seq 1 60); do
    if mysqladmin ping -h "$MYSQL_HOST" -P "${MYSQL_PORT:-3306}" -u "$MYSQL_USER" --silent; then
      return 0
    fi
    sleep 2
  done
  echo "MySQL did not become ready" >&2
  return 1
}

create_backup() {
  local stamp temporary final database dump artifact
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  temporary="$backup_root/.${stamp}.partial"
  final="$backup_root/$stamp"
  mkdir -p "$backup_root"
  rm -rf -- "$temporary"
  mkdir -p "$temporary"

  for database in enterprise_core enterprise_workload enterprise_ml; do
    dump="$temporary/${database}.sql.gz"
    mysqldump -h "$MYSQL_HOST" -P "${MYSQL_PORT:-3306}" -u "$MYSQL_USER" \
      --single-transaction --quick --routines --events --triggers --no-tablespaces \
      --set-gtid-purged=OFF --databases "$database" | gzip -9 > "$dump"
  done

  mysql -h "$MYSQL_HOST" -P "${MYSQL_PORT:-3306}" -u "$MYSQL_USER" \
    --batch --raw --skip-column-names enterprise_ml \
    -e "SELECT id,version,active,artifact_path,feature_schema_hash,training_dataset_hash,trained_at FROM ml_model_versions ORDER BY trained_at DESC,id DESC" \
    > "$temporary/model-registry.tsv"

  if [[ -d "$model_root" ]]; then
    tar -C "$model_root" -czf "$temporary/model-artifacts.tar.gz" .
  else
    tar -czf "$temporary/model-artifacts.tar.gz" --files-from /dev/null
  fi

  printf 'created_at_utc=%s\nretention_count=%s\n' "$stamp" "$retention" > "$temporary/manifest.properties"
  (
    cd "$temporary"
    sha256sum enterprise_core.sql.gz enterprise_workload.sql.gz enterprise_ml.sql.gz \
      model-registry.tsv model-artifacts.tar.gz manifest.properties > SHA256SUMS
  )
  mv "$temporary" "$final"

  mapfile -t backups < <(find "$backup_root" -mindepth 1 -maxdepth 1 -type d \
    -name '????????T??????Z' -printf '%f\n' | sort -r)
  if (( ${#backups[@]} > retention )); then
    for artifact in "${backups[@]:retention}"; do
      rm -rf -- "$backup_root/$artifact"
    done
  fi
  echo "Backup completed: $final"
}

wait_for_mysql
if [[ "${BACKUP_ONCE:-false}" == "true" ]]; then
  create_backup
  exit 0
fi

while true; do
  create_backup
  sleep "$interval"
done
