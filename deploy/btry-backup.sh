#!/usr/bin/env bash
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
find "$BACKUP_DIR" -type f -name "$DB_NAME-*.sql.gz" -mtime +"$RETENTION_DAYS" -delete
echo "backup created: $TARGET"
