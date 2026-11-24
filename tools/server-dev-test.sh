#!/usr/bin/env bash
set -euo pipefail

cd /opt/etry
MODE="${1:-unit}"

AVAIL=$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo)
if [ "$AVAIL" -lt 900 ]; then
  echo "ERROR: available memory ${AVAIL}MB < 900MB; abort to protect live service" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
. /opt/etry/.env.docker
set +a

IT_ENV=/opt/etry/.env.it
umask 077
cat > "$IT_ENV" <<EOF
IT_DB_URL=jdbc:mysql://127.0.0.1:13306/btry_it?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&allowMultiQueries=true
IT_DB_USER=${DB_USER}
IT_DB_PASSWORD=${DB_PASSWORD}
IT_REDIS_HOST=127.0.0.1
IT_REDIS_PORT=16379
IT_REDIS_PASSWORD=${REDIS_PASSWORD}
IT_REDIS_DATABASE=15
COS_SECRET_ID=ci-dummy
COS_SECRET_KEY=ci-dummy
EOF
chmod 600 "$IT_ENV"

MVN_IMAGE=maven:3.9.9-eclipse-temurin-17
COMMON=(sudo -n docker run --rm --network host --memory=1200m --memory-swap=1500m
  -v /opt/etry/server:/workspace -w /workspace -v btry-m2:/root/.m2 -e TZ=Asia/Shanghai)

case "$MODE" in
  unit)
    "${COMMON[@]}" "$MVN_IMAGE" mvn -B -s docker/maven-settings.xml test
    ;;
  it)
    "${COMMON[@]}" --env-file "$IT_ENV" "$MVN_IMAGE" mvn -B -s docker/maven-settings.xml -Pintegration test
    ;;
  all)
    "${COMMON[@]}" "$MVN_IMAGE" mvn -B -s docker/maven-settings.xml test
    "${COMMON[@]}" --env-file "$IT_ENV" "$MVN_IMAGE" mvn -B -s docker/maven-settings.xml -Pintegration test
    ;;
  *)
    echo "usage: $0 [unit|it|all]" >&2
    exit 2
    ;;
esac
