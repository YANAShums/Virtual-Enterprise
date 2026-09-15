#!/bin/bash
set -euo pipefail
# Also run by db-bootstrap for existing named volumes.
[[ "${DB_USERNAME}" =~ ^[a-zA-Z0-9_]+$ ]] || exit 1
MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql -h "${MYSQL_HOST:-localhost}" -uroot <<SQL
CREATE DATABASE IF NOT EXISTS enterprise_ml CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON enterprise_ml.* TO '${DB_USERNAME}'@'%';
SQL
