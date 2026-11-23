#!/usr/bin/env bash
# 阶段6 冒烟：运营资料管理 / 权限点矩阵 / 客服权益视图 / 财务导出 / 审计 / 空库种子。
# 依赖：本地 8084 服务（btry_smoke 库），mysql 客户端，python。从 server 目录运行。
set -u
export PYTHONIOENCODING=utf-8
BASE=http://127.0.0.1:8084
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
PASS=0; FAIL=0

ok()   { PASS=$((PASS+1)); echo "PASS $1"; }
bad()  { FAIL=$((FAIL+1)); echo "FAIL $1 -- $2"; }
check(){ if [ "$2" = "$3" ]; then ok "$1"; else bad "$1" "expect [$3] got [$2]"; fi; }

jget() { python -c "import sys,json;d=json.load(sys.stdin);print(eval('d'+sys.argv[1]))" "$1" 2>/dev/null; }
post_json() {
  if [ -n "${3:-}" ]; then
    curl -s -X POST "$BASE$1" -H 'Content-Type: application/json' -H "Authorization: Bearer $3" --data @"$2"
  else
    curl -s -X POST "$BASE$1" -H 'Content-Type: application/json' --data @"$2"
  fi
}
put_json() { curl -s -X PUT "$BASE$1" -H 'Content-Type: application/json' -H "Authorization: Bearer $2" --data @"$3"; }
code() { curl -s -o "$TMP/body" -w "%{http_code}" "$@"; }
sql()  { mysql -uroot -proot btry_smoke -N -B -e "$1" 2>/dev/null; }

register() { # username -> token
  printf '{"username":"%s","password":"Buyer#2026x"}' "$1" > "$TMP/reg.json"
  post_json /api/auth/register "$TMP/reg.json" | jget "['token']"
}
grant() { # username role
  sql "INSERT IGNORE INTO t_user_role (user_id, role_id) SELECT u.id, r.id FROM t_user u, t_role r WHERE u.username='$1' AND r.code='$2'"
}

# ---------- 登录 ----------
printf '{"username":"admin","password":"Smoke#Admin2026y"}' > "$TMP/admin.json"
post_json /api/auth/login "$TMP/admin.json" > "$TMP/admin.out"
ADMIN=$(jget "['token']" < "$TMP/admin.out")
if [ -z "$ADMIN" ]; then
  printf '{"username":"admin","password":"Smoke#Admin2026x"}' > "$TMP/admin.json"
  post_json /api/auth/login "$TMP/admin.json" > "$TMP/admin.out"
  printf '{"oldPassword":"Smoke#Admin2026x","newPassword":"Smoke#Admin2026y"}' > "$TMP/pwd.json"
  T0=$(jget "['token']" < "$TMP/admin.out")
  curl -s -X PUT "$BASE/api/auth/password" -H 'Content-Type: application/json' -H "Authorization: Bearer $T0" --data @"$TMP/pwd.json" > /dev/null
  printf '{"username":"admin","password":"Smoke#Admin2026y"}' > "$TMP/admin.json"
  post_json /api/auth/login "$TMP/admin.json" > "$TMP/admin.out"
  ADMIN=$(jget "['token']" < "$TMP/admin.out")
fi
if [ -n "$ADMIN" ]; then ok "1 管理员登录"; else bad "1 管理员登录" "$(cat "$TMP/admin.out")"; fi

S6=$RANDOM$RANDOM
BUYER="s6_buyer_$S6"; OP="s6_op_$S6"; CS="s6_cs_$S6"; FIN="s6_fin_$S6"
TOKEN=$(register "$BUYER"); OPT=$(register "$OP"); CST=$(register "$CS"); FINT=$(register "$FIN")
grant "$OP" OPERATOR; grant "$CS" CUSTOMER_SERVICE; grant "$FIN" FINANCE
if [ -n "$TOKEN" ] && [ -n "$OPT" ] && [ -n "$CST" ] && [ -n "$FINT" ]; then ok "2 四身份注册+授角色"; else bad "2 四身份注册+授角色" "token missing"; fi

# ---------- 权限点矩阵 ----------
C=$(code -H "Authorization: Bearer $ADMIN" "$BASE/api/admin/materials")
check "3 超管资料列表200" "$C" "200"
C=$(code -H "Authorization: Bearer $TOKEN" "$BASE/api/admin/materials")
check "4 买家资料列表403" "$C" "403"
check "5 403文案" "$(jget "['message']" < "$TMP/body")" "没有权限执行该操作"
C=$(code "$BASE/api/admin/materials")
check "6 游客资料列表401" "$C" "401"
C=$(code -H "Authorization: Bearer $OPT" "$BASE/api/admin/materials")
check "7 运营资料列表200" "$C" "200"
C=$(code -H "Authorization: Bearer $OPT" "$BASE/api/admin/orders")
check "8 运营订单列表403" "$C" "403"
C=$(code -H "Authorization: Bearer $CST" "$BASE/api/admin/orders")
check "9 客服订单列表200" "$C" "200"
C=$(code -H "Authorization: Bearer $CST" "$BASE/api/admin/users")
check "10 客服用户列表200" "$C" "200"
C=$(code -X POST -H "Authorization: Bearer $FINT" "$BASE/api/admin/orders/NOPE/fulfill")
check "11 财务补履约403" "$C" "403"
C=$(code -X POST -H "Authorization: Bearer $ADMIN" "$BASE/api/admin/orders/NOPE/fulfill")
check "12 超管补履约过鉴权404" "$C" "404"
C=$(code -H "Authorization: Bearer $FINT" "$BASE/api/admin/reconciliation/statements")
check "13 财务对账列表200" "$C" "200"

# ---------- 运营 CRUD ----------
printf '{"title":"阶段6冒烟试卷_%s","category":"EXAM_PAPER","stage":"SENIOR","subject":"数学","summary":"冒烟创建","priceCents":880,"pageCount":12,"previewPages":2}' "$S6" > "$TMP/create.json"
post_json /api/admin/materials "$TMP/create.json" "$OPT" > "$TMP/create.out"
MID=$(jget "['id']" < "$TMP/create.out")
check "14 运营新建默认下架" "$(jget "['saleStatus']" < "$TMP/create.out")" "OFF_SALE"
check "15 新建默认不推荐" "$(jget "['featured']" < "$TMP/create.out")" "False"

printf '{"saleStatus":"ON_SALE"}' > "$TMP/sale.json"
C=$(code -X PUT -H "Authorization: Bearer $OPT" -H 'Content-Type: application/json' --data @"$TMP/sale.json" "$BASE/api/admin/materials/$MID/sale-status")
check "16 未挂文件上架400" "$C" "400"
check "17 上架守卫文案" "$(jget "['message']" < "$TMP/body")" "上架前必须已上传文件且价格大于 0"

printf 'MZ-not-pdf' > "$TMP/fake.pdf"
C=$(code -X POST -H "Authorization: Bearer $OPT" -F "file=@$TMP/fake.pdf" "$BASE/api/admin/materials/$MID/file")
check "18 非PDF上传400" "$C" "400"
check "19 非PDF文案" "$(jget "['message']" < "$TMP/body")" "仅支持 PDF 文件"

curl -s "$BASE/api/materials/1/preview" -o "$TMP/real.pdf"
C=$(code -X POST -H "Authorization: Bearer $OPT" -F "file=@$TMP/real.pdf" "$BASE/api/admin/materials/$MID/file")
check "20 上传PDF200" "$C" "200"
NEWKEY=$(jget "['fileKey']" < "$TMP/body")
case "$NEWKEY" in material/upload-$MID-*) ok "21 上传fileKey规范";; *) bad "21 上传fileKey规范" "$NEWKEY";; esac

put_json "/api/admin/materials/$MID/sale-status" "$OPT" "$TMP/sale.json" > "$TMP/sale.out"
check "22 挂文件后上架" "$(jget "['saleStatus']" < "$TMP/sale.out")" "ON_SALE"

printf '{"title":"阶段6冒烟改名_%s","category":"TEXTBOOK","stage":"JUNIOR","subject":"物理","summary":"改","priceCents":1280,"pageCount":30,"previewPages":4}' "$S6" > "$TMP/update.json"
put_json "/api/admin/materials/$MID" "$OPT" "$TMP/update.json" > "$TMP/update.out"
check "23 编辑价格" "$(jget "['priceCents']" < "$TMP/update.out")" "1280"
check "24 编辑保持上架" "$(jget "['saleStatus']" < "$TMP/update.out")" "ON_SALE"

printf '{"featured":true}' > "$TMP/feat.json"
put_json "/api/admin/materials/$MID/featured" "$OPT" "$TMP/feat.json" > "$TMP/feat.out"
check "25 设推荐位" "$(jget "['featured']" < "$TMP/feat.out")" "True"
FIRST=$(curl -s "$BASE/api/materials" | jget "['items'][0]['title']")
check "26 推荐位排序第一" "$FIRST" "阶段6冒烟改名_$S6"

# Git Bash 原生 curl 按 ANSI(GBK) 转码命令行中文，且读不了 MSYS 临时路径；
# 用 python 从 UTF-8 文件预编码查询词。
printf '阶段6冒烟改名_%s' "$S6" > .smoke-kw
KW_ENC=$(python -c "import urllib.parse;print(urllib.parse.quote(open('.smoke-kw',encoding='utf-8').read()))")
rm -f .smoke-kw
C=$(code -H "Authorization: Bearer $OPT" "$BASE/api/admin/materials?keyword=$KW_ENC&saleStatus=ON_SALE")
check "27 管理端筛选200" "$C" "200"
check "28 筛选命中1条" "$(jget "['total']" < "$TMP/body")" "1"

AUD=$(sql "SELECT COUNT(*) FROM t_admin_audit_log WHERE action='MATERIAL_CREATE' AND target_id=$MID AND success=1")
check "29 新建审计落库" "$AUD" "1"
AUD=$(sql "SELECT COUNT(*) FROM t_admin_audit_log WHERE action='MATERIAL_UPLOAD' AND target_id=$MID AND success=1")
check "30 上传成功审计落库" "$AUD" "1"
AUD=$(sql "SELECT COUNT(*) FROM t_admin_audit_log WHERE action='MATERIAL_SALE_STATUS' AND target_id=$MID")
check "31 上下架审计含失败尝试" "$AUD" "2"

# ---------- 客服权益视图 ----------
BUYER_ID=$(sql "SELECT id FROM t_user WHERE username='$BUYER'")
sql "INSERT IGNORE INTO t_entitlement (user_id, product_id, product_type, source, status, display_order, granted_at) VALUES ($BUYER_ID, 1, 'MATERIAL', 'ADMIN', 'ACTIVE', 1, CURRENT_TIMESTAMP(3))"
C=$(code -H "Authorization: Bearer $CST" "$BASE/api/admin/users/$BUYER_ID/entitlements")
check "32 客服查权益200" "$C" "200"
check "33 权益含资料1" "$(jget "[0]['productId']" < "$TMP/body")" "1"
check "34 权益类型" "$(jget "[0]['productType']" < "$TMP/body")" "MATERIAL"
C=$(code -H "Authorization: Bearer $TOKEN" "$BASE/api/admin/users/$BUYER_ID/entitlements")
check "35 买家查他人权益403" "$C" "403"

# ---------- 财务导出 ----------
C=$(curl -s -H "Authorization: Bearer $FINT" -o "$TMP/orders.xlsx" -w "%{http_code}" "$BASE/api/admin/orders/export")
check "36 订单导出200" "$C" "200"
check "37 订单导出xlsx魔数" "$(head -c 2 "$TMP/orders.xlsx")" "PK"
C=$(curl -s -H "Authorization: Bearer $FINT" -o "$TMP/breaks.xlsx" -w "%{http_code}" "$BASE/api/admin/reconciliation/runs/999/breaks/export")
check "38 差异导出200" "$C" "200"
check "39 差异导出xlsx魔数" "$(head -c 2 "$TMP/breaks.xlsx")" "PK"
C=$(code -H "Authorization: Bearer $TOKEN" "$BASE/api/admin/orders/export")
check "40 买家导出403" "$C" "403"

# ---------- 上传文件交付（新买家购买冒烟资料） ----------
B2="s6_dl_$S6"; T2=$(register "$B2")
printf '{"materialId":%s}' "$MID" > "$TMP/cart.json"
curl -s -X POST -H "Authorization: Bearer $T2" -H 'Content-Type: application/json' --data @"$TMP/cart.json" "$BASE/api/cart" > /dev/null
printf '{"materialIds":[%s],"clientRequestId":"s6-%s"}' "$MID" "$S6" > "$TMP/checkout.json"
post_json /api/cart/checkout "$TMP/checkout.json" "$T2" > "$TMP/order.out"
ORDER=$(jget "['orderNo']" < "$TMP/order.out")
curl -s -X POST -H "Authorization: Bearer $T2" "$BASE/api/orders/$ORDER/pay" > /dev/null
printf '{"scenario":"SUCCESS"}' > "$TMP/event.json"
post_json "/api/payments/mock/$ORDER/events" "$TMP/event.json" "$T2" > /dev/null
ST=""
for i in $(seq 1 20); do
  sleep 1
  ST=$(curl -s -H "Authorization: Bearer $T2" "$BASE/api/orders/$ORDER" | jget "['order']['status']")
  [ "$ST" = "FULFILLED" ] && break
done
check "41 冒烟资料履约" "$ST" "FULFILLED"
C=$(curl -s -H "Authorization: Bearer $T2" -o "$TMP/dl.pdf" -w "%{http_code}" "$BASE/api/library/materials/$MID/download")
check "42 上传文件可下载" "$C" "200"
check "43 下载PDF魔数" "$(head -c 4 "$TMP/dl.pdf")" "%PDF"

# ---------- 清理冒烟数据 ----------
printf '{"saleStatus":"OFF_SALE"}' > "$TMP/off.json"
put_json "/api/admin/materials/$MID/sale-status" "$ADMIN" "$TMP/off.json" > /dev/null
printf '{"featured":false}' > "$TMP/feat.json"
put_json "/api/admin/materials/$MID/featured" "$ADMIN" "$TMP/feat.json" > /dev/null
sql "DELETE FROM t_entitlement WHERE user_id=$BUYER_ID AND product_id=1 AND source='ADMIN'"

echo "----------------------------------------"
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ]