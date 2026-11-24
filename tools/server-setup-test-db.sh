#!/usr/bin/env bash
set -euo pipefail

sudo -n docker exec -i btry-mysql-1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' <<'SQL'
CREATE DATABASE IF NOT EXISTS btry_it CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON btry_it.* TO 'btry'@'%';
FLUSH PRIVILEGES;
SQL

echo "btry_it ready"
