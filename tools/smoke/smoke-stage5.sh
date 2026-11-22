#!/usr/bin/env bash
# 阶段5 冒烟：公开试读 / 权益下载 / 书架 / 退款回收 / 并发懒生成。
# 依赖：本地 8084 服务（btry_smoke 库），mysql 客户端，python。
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

post_json() { # url json-file token -> body
  if [ -n "${3:-}" ]; then
    curl -s -X POST "$BASE$1" -H 'Content-Type: application/json' -H "Authorization: Bearer $3" --data @"$2"
  else
    curl -s -X POST "$BASE$1" -H 'Content-Type: application/json' --data @"$2"
  fi
}
code() { curl -s -o "$TMP/body" -w "%{http_code}" "$@"; }

# ---------- 公开试读 ----------
C=$(code "$BASE/api/materials/1/preview")
check "1 游客试读200" "$C" "200"
CT=$(curl -s -o /dev/null -w "%{content_type}" "$BASE/api/materials/1/preview")
check "2 试读content-type" "$CT" "application/pdf"
curl -s "$BASE/api/materials/1/preview" -o "$TMP/preview.pdf"
MAGIC=$(head -c 4 "$TMP/preview.pdf")
check "3 试读PDF魔数" "$MAGIC" "%PDF"
SIZE=$(wc -c < "$TMP/preview.pdf")
if [ "$SIZE" -gt 1000 ]; then ok "4 试读文件大小($SIZE)"; else bad "4 试读文件大小" "$SIZE"; fi

C=$(code "$BASE/api/materials/5/preview")
check "5 下架试读404" "$C" "404"
check "6 下架试读文案" "$(jget "['message']" < "$TMP/body")" "资料不存在或已下架"
C=$(code "$BASE/api/materials/999999/preview")
check "7 不存在试读404" "$C" "404"

# ---------- 鉴权与权益门槛 ----------
C=$(code "$BASE/api/library/materials/1/download")
check "8 游客下载401" "$C" "401"
C=$(code "$BASE/api/library/materials")
check "9 游客书架401" "$C" "401"

BUYER="s5_buyer_$RANDOM$RANDOM"
printf '{"username":"%s","password":"Buyer#2026x"}' "$BUYER" > "$TMP/reg.json"
post_json /api/auth/register "$TMP/reg.json" > "$TMP/reg.out"
TOKEN=$(jget "['token']" < "$TMP/reg.out")
if [ -n "$TOKEN" ]; then ok "10 注册买家"; else bad "10 注册买家" "$(cat "$TMP/reg.out")"; fi

C=$(code -H "Authorization: Bearer $TOKEN" "$BASE/api/library/materials/1/download")
check "11 未购下载403" "$C" "403"
check "12 未购下载文案" "$(jget "['message']" < "$TMP/body")" "尚未购买该资料，无法下载"
check "13 新买家书架为空" "$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE/api/library/materials")" "[]"

# ---------- 购买 → 履约 → 下载 ----------
printf '{"materialId":2}' > "$TMP/cart.json"
C=$(code -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' --data @"$TMP/cart.json" "$BASE/api/cart")
check "14 加购资料2" "$C" "200"
printf '{"materialIds":[2],"clientRequestId":"s5-%s"}' "$RANDOM$RANDOM" > "$TMP/checkout.json"
post_json /api/cart/checkout "$TMP/checkout.json" "$TOKEN" > "$TMP/order.out"
ORDER=$(jget "['orderNo']" < "$TMP/order.out")
if [ -n "$ORDER" ]; then ok "15 结算下单($ORDER)"; else bad "15 结算下单" "$(cat "$TMP/order.out")"; fi

C=$(code -X POST -H "Authorization: Bearer $TOKEN" "$BASE/api/orders/$ORDER/pay")
check "16 发起支付" "$C" "200"
printf '{"scenario":"SUCCESS"}' > "$TMP/event.json"
post_json "/api/payments/mock/$ORDER/events" "$TMP/event.json" "$TOKEN" > "$TMP/event.out"
STATUS=""
for i in $(seq 1 20); do
  sleep 1
  STATUS=$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE/api/orders/$ORDER" | jget "['order']['status']")
  [ "$STATUS" = "FULFILLED" ] && break
done
check "17 履约完成" "$STATUS" "FULFILLED"

LIB=$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE/api/library/materials")
check "18 书架含资料2" "$(echo "$LIB" | jget "[0]['id']")" "2"
check "19 书架可下载" "$(echo "$LIB" | jget "[0]['downloadable']")" "True"

C=$(curl -s -H "Authorization: Bearer $TOKEN" -o "$TMP/download.pdf" -w "%{http_code}" "$BASE/api/library/materials/2/download")
check "20 已购下载200" "$C" "200"
check "21 下载PDF魔数" "$(head -c 4 "$TMP/download.pdf")" "%PDF"
CD=$(curl -s -D - -o /dev/null -H "Authorization: Bearer $TOKEN" "$BASE/api/library/materials/2/download" | grep -i content-disposition)
case "$CD" in *attachment*) ok "22 下载附件头";; *) bad "22 下载附件头" "$CD";; esac

# ---------- 管理员退款 → 权益回收 ----------
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
if [ -n "$ADMIN" ]; then ok "23 管理员登录"; else bad "23 管理员登录" "$(cat "$TMP/admin.out")"; fi

printf '{"reason":"阶段5冒烟退款","simulateFailure":false}' > "$TMP/refund.json"
C=$(code -X POST -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' --data @"$TMP/refund.json" "$BASE/api/admin/orders/$ORDER/refund")
check "24 管理员退款" "$C" "200"
RSTATUS=""; LIBLEN=""
for i in $(seq 1 20); do
  sleep 1
  RSTATUS=$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE/api/orders/$ORDER" | jget "['order']['status']")
  LIBLEN=$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE/api/library/materials" | python -c "import sys,json;print(len(json.load(sys.stdin)))")
  [ "$RSTATUS" = "REFUNDED" ] && [ "$LIBLEN" = "0" ] && break
done
check "25 退款完成" "$RSTATUS" "REFUNDED"
check "26 书架回收为空" "$LIBLEN" "0"
C=$(code -H "Authorization: Bearer $TOKEN" "$BASE/api/library/materials/2/download")
check "27 退款后下载403" "$C" "403"

# ---------- 并发下载懒生成 ----------
printf '{"materialId":4}' > "$TMP/cart.json"
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' --data @"$TMP/cart.json" "$BASE/api/cart" > /dev/null
printf '{"materialIds":[4],"clientRequestId":"s5c-%s"}' "$RANDOM$RANDOM" > "$TMP/checkout.json"
post_json /api/cart/checkout "$TMP/checkout.json" "$TOKEN" > "$TMP/order2.out"
ORDER2=$(jget "['orderNo']" < "$TMP/order2.out")
curl -s -X POST -H "Authorization: Bearer $TOKEN" "$BASE/api/orders/$ORDER2/pay" > /dev/null
post_json "/api/payments/mock/$ORDER2/events" "$TMP/event.json" "$TOKEN" > /dev/null
for i in $(seq 1 20); do
  sleep 1
  S2=$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE/api/orders/$ORDER2" | jget "['order']['status']")
  [ "$S2" = "FULFILLED" ] && break
done
check "28 并发前置履约" "$S2" "FULFILLED"

FILEKEY=$(mysql -uroot -proot btry_smoke -N -B -e "SELECT file_key FROM t_material WHERE id=4" 2>/dev/null)
if [ -n "$FILEKEY" ]; then rm -f "data/materials/$FILEKEY"; fi
for i in 1 2 3 4 5 6; do
  ( curl -s -o "$TMP/c$i.pdf" -w "%{http_code}" -H "Authorization: Bearer $TOKEN" "$BASE/api/library/materials/4/download" > "$TMP/c$i.code" ) &
done
wait
ALL_OK=1
for i in 1 2 3 4 5 6; do
  [ "$(cat "$TMP/c$i.code")" = "200" ] || ALL_OK=0
  [ "$(head -c 4 "$TMP/c$i.pdf")" = "%PDF" ] || ALL_OK=0
done
check "29 六并发下载全200且PDF" "$ALL_OK" "1"
if [ -n "$FILEKEY" ] && [ -f "data/materials/$FILEKEY" ]; then ok "30 懒生成文件落盘"; else bad "30 懒生成文件落盘" "$FILEKEY"; fi

echo "----------------------------------------"
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ]