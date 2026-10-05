#!/usr/bin/env python3
"""Static final-stage checks that do not print or inspect secret values."""
from __future__ import annotations

import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]


def tracked_files() -> list[pathlib.Path]:
    output = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    return [ROOT / item.decode("utf-8") for item in output.split(b"\0") if item]


def main() -> int:
    failures: list[str] = []
    forbidden_suffixes = {".pem", ".key", ".p12", ".pfx", ".jks"}
    private_key_markers = ["-----BEGIN " + kind + "-----" for kind in ("PRIVATE KEY", "RSA PRIVATE KEY")]
    for path in tracked_files():
        if path.suffix.lower() in forbidden_suffixes:
            failures.append(f"secret-like file is tracked: {path.relative_to(ROOT)}")
        if path.is_file() and path.stat().st_size < 2_000_000:
            text = path.read_text("utf-8", errors="ignore")
            if any(marker in text for marker in private_key_markers):
                failures.append(f"private key marker is tracked: {path.relative_to(ROOT)}")
    required = [
        ROOT / "ops/backup/backup.sh", ROOT / "ops/backup/restore.sh",
        ROOT / "ops/backup/docker-compose.restore-drill.yml", ROOT / ".env.production.example",
        ROOT / "docs/DEPLOYMENT.md", ROOT / "docs/acceptance-checklist.md",
    ]
    failures.extend(f"required artifact is missing: {path.relative_to(ROOT)}" for path in required if not path.is_file())
    ignored = subprocess.run(["git", "check-ignore", "-q", "backups/stage14-probe"], cwd=ROOT).returncode == 0
    if not ignored:
        failures.append("backups directory is not ignored by Git")
    compose = subprocess.run(["docker", "compose", "config", "--quiet"], cwd=ROOT,
                             capture_output=True, text=True)
    if compose.returncode:
        failures.append("docker compose configuration is invalid")
    if failures:
        print("Stage 14 static checks failed:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print("Stage 14 static checks passed: tracked secrets, backup artifacts, ignore rules and Compose configuration.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
