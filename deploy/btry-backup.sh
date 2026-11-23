#!/usr/bin/env bash
# 每日备份：MySQL 全量 + 资料 PDF 存储目录。
# 恢复：
#   gunzip < "$BACKUP_DIR/$DB_NAME-<stamp>.sql.gz" | mysql -h127.0.0.1 -u"$DB_USER" -p "$DB_NAME"
#   tar -xzf "$BACKUP_DIR/materials-<stamp>.tar.gz" -C "$(dirname "$MATERIAL_STORAGE_ROOT")"
set -euo pipefail

APP_DIR=/opt/btry
ENV_FILE="$APP_DIR/.env"
BACKUP_DIR="${BTRY_BACKUP_DIR:-$APP_DIR/backups}"
DB_NAME="${BTRY_DB_NAME:-btry_edu}"
RETENTION_DAYS="${BTRY_BACKUP_RETENTION_DAYS:-14}"

[ -f "$ENV_FILE" ] || { echo "missing $ENV_FILE" >&2; exit 1; }
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

mkdir -p "$BACKUP_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
TARGET="$BACKUP_DIR/$DB_NAME-$STAMP.sql.gz"
TMP="$TARGET.tmp"

MYSQL_PWD="$DB_PASSWORD" mysqldump \
  --single-transaction --quick --routines --triggers \
  -h127.0.0.1 -u"$DB_USER" "$DB_NAME" | gzip > "$TMP"
mv "$TMP" "$TARGET"
echo "backup created: $TARGET"

MATERIAL_ROOT="${MATERIAL_STORAGE_ROOT:-$APP_DIR/data/materials}"
if [ -d "$MATERIAL_ROOT" ]; then
  MATERIAL_TARGET="$BACKUP_DIR/materials-$STAMP.tar.gz"
  tar -czf "$MATERIAL_TARGET.tmp" -C "$(dirname "$MATERIAL_ROOT")" "$(basename "$MATERIAL_ROOT")"
  mv "$MATERIAL_TARGET.tmp" "$MATERIAL_TARGET"
  echo "materials backup created: $MATERIAL_TARGET"
else
  echo "materials dir not found, skip: $MATERIAL_ROOT"
fi

find "$BACKUP_DIR" -type f -name "$DB_NAME-*.sql.gz" -mtime +"$RETENTION_DAYS" -delete
find "$BACKUP_DIR" -type f -name "materials-*.tar.gz" -mtime +"$RETENTION_DAYS" -delete
