#!/usr/bin/env bash
set -euo pipefail

sudo -n docker exec -i btry-mysql-1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" btry_edu' <<'SQL'
DELETE FROM t_user_role WHERE user_id IN (SELECT id FROM t_user WHERE username LIKE 'load_probe_%');
DELETE FROM t_user WHERE username LIKE 'load_probe_%';
SQL

sudo -n docker exec btry-mysql-1 sh -c 'mysql -ubtry -p"$MYSQL_PASSWORD" -N -B btry_edu -e "SELECT COUNT(*) FROM t_user;"'
