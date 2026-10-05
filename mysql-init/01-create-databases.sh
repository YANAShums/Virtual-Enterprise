#!/bin/bash
set -e
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS enterprise_core CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS enterprise_workload CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SQL
