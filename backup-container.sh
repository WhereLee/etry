#!/usr/bin/env bash
set -euo pipefail

APP_DIR=/opt/etry
BACKUP_DIR="$APP_DIR/backups"
RETENTION_DAYS=14
STAMP="$(date +%Y%m%d-%H%M%S)"
DEST="$BACKUP_DIR/$STAMP"
mkdir -p "$DEST"

sudo -n docker exec btry-mysql-1 sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump -ubtry --single-transaction --routines --triggers btry_edu' | gzip > "$DEST/btry_edu.sql.gz"

sudo -n docker run --rm -v btry_material-data:/data:ro -v "$DEST":/backup alpine:3.20 sh -c 'tar -czf /backup/materials.tar.gz -C /data .'
sudo -n chown -R ubuntu:ubuntu "$DEST"

coscmd upload -r "$DEST/" "/backups/btry-container-daily/$STAMP/" >/dev/null 2>&1

find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -mtime +"$RETENTION_DAYS" -exec rm -rf {} +

echo "backup ok: $DEST"
