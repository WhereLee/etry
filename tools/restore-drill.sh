#!/usr/bin/env bash
set -euo pipefail

STAMP="$(ls -1d /opt/etry/backups/20* 2>/dev/null | sort | tail -1 | xargs -n1 basename)"
[ -n "$STAMP" ] || { echo "no local backup found" >&2; exit 1; }
SRC="/opt/etry/backups/$STAMP"
COS_PREFIX="/backups/btry-container-daily/$STAMP"
WORK="/opt/etry/restore-drill/$STAMP"
EVID_DIR="/opt/etry/docs/evidence/$(date +%F)"
mkdir -p "$WORK" "$EVID_DIR"
START=$(date +%s)

echo "==> download from COS: $COS_PREFIX"
coscmd download "$COS_PREFIX/btry_edu.sql.gz" "$WORK/btry_edu.sql.gz" >/dev/null
coscmd download "$COS_PREFIX/materials.tar.gz" "$WORK/materials.tar.gz" >/dev/null

echo "==> checksum compare (local vs COS)"
LOCAL_DB_SHA=$(sha256sum "$SRC/btry_edu.sql.gz" | cut -d' ' -f1)
COS_DB_SHA=$(sha256sum "$WORK/btry_edu.sql.gz" | cut -d' ' -f1)
[ "$LOCAL_DB_SHA" = "$COS_DB_SHA" ] || { echo "DB backup checksum mismatch" >&2; exit 1; }

echo "==> restore into btry_restore"
sudo -n docker exec -i btry-mysql-1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS btry_restore; CREATE DATABASE btry_restore CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"'
gzip -dc "$WORK/btry_edu.sql.gz" > "$WORK/dump.sql"
sudo -n docker exec -i btry-mysql-1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" btry_restore' < "$WORK/dump.sql"
rm -f "$WORK/dump.sql"

QUERY='SELECT COUNT(*) FROM t_user; SELECT COUNT(*) FROM t_material; SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;'
PROD=$(sudo -n docker exec btry-mysql-1 sh -c "mysql -ubtry -p\"\$MYSQL_PASSWORD\" -N -B btry_edu -e \"$QUERY\"" 2>/dev/null | tr '\n' ' ')
REST=$(sudo -n docker exec btry-mysql-1 sh -c "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -N -B btry_restore -e \"$QUERY\"" 2>/dev/null | tr '\n' ' ')
echo "prod:    $PROD"
echo "restore: $REST"
[ "$PROD" = "$REST" ] || { echo "restore data mismatch" >&2; exit 1; }

echo "==> verify materials"
PROD_FILES=$(sudo -n docker exec btry-backend-1 sh -c 'find /app/data/materials -type f 2>/dev/null | wc -l' | tr -d ' ')
tar -xzf "$WORK/materials.tar.gz" -C "$WORK" --one-top-level=materials
REST_FILES=$(find "$WORK/materials" -type f | wc -l | tr -d ' ')
echo "prod material files: $PROD_FILES, backup files: $REST_FILES"
[ "$PROD_FILES" = "$REST_FILES" ] || { echo "material file count mismatch" >&2; exit 1; }

END=$(date +%s)
RTO=$((END-START))
BACKUP_TS=$(date -r "$SRC/btry_edu.sql.gz" '+%Y-%m-%d %H:%M:%S')
NOW=$(date '+%Y-%m-%d %H:%M:%S')
RPO_MIN=$(( (END - $(date -r "$SRC/btry_edu.sql.gz" +%s)) / 60 ))

cat > "$EVID_DIR/restore-drill.md" <<EOF
# 备份恢复演练（$(date +%F)）

- 备份时间：$BACKUP_TS
- 演练完成：$NOW
- 备份源：COS $COS_PREFIX；本地与 COS SHA256 一致（$LOCAL_DB_SHA）
- 恢复目标：容器 MySQL \`btry_restore\`（不影响生产 \`btry_edu\`）
- 数据对比（用户/资料/Flyway）：prod=[$PROD] restore=[$REST]
- 资料文件数：prod=$PROD_FILES backup=$REST_FILES
- RTO（下载+导入+校验）：${RTO}s
- RPO：约 ${RPO_MIN} 分钟（最后一次备份至演练）
- 结论：PASS
EOF

echo "==> cleanup"
sudo -n docker exec -i btry-mysql-1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS btry_restore;"'
rm -rf "$WORK"
echo "evidence: $EVID_DIR/restore-drill.md"
cat "$EVID_DIR/restore-drill.md"
