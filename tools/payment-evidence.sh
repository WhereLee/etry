#!/usr/bin/env bash
set -euo pipefail

ROOT="${ETRY_ROOT:-/opt/etry}"
OUT="${1:-$ROOT/docs/evidence/$(date +%F)/payment-evidence-$(date +%H%M%S).txt}"
MYSQL_PASSWORD="$(sudo -n grep '^MYSQL_ROOT_PASSWORD=' "$ROOT/.env.docker" | cut -d= -f2-)"

if [ -z "$MYSQL_PASSWORD" ]; then
  echo "ERROR: MYSQL_ROOT_PASSWORD is missing" >&2
  exit 1
fi

mkdir -p "$(dirname "$OUT")"
exec > >(tee "$OUT") 2>&1

echo "captured_at=$(date -Iseconds)"
echo "project=$ROOT"
echo "git_commit=$(git -C "$ROOT" rev-parse HEAD)"
echo "backend_image=$(sudo -n docker inspect btry-backend:latest --format '{{.Id}}')"
echo "backend_started=$(sudo -n docker inspect btry-backend-1 --format '{{.State.StartedAt}}')"
echo "external_learn=$(curl -s -o /dev/null -w '%{http_code}' https://whereri.site/learn/)"
echo "external_api=$(curl -s -o /dev/null -w '%{http_code}' https://whereri.site/api/health)"
echo "readiness=$(sudo -n docker exec btry-backend-1 wget -qO- http://127.0.0.1:9091/actuator/health/readiness)"

echo
echo "== runtime payment libraries =="
sudo -n docker exec btry-backend-1 sh -c "unzip -l app.jar | grep -E 'amqp-client|opentelemetry-api|opentelemetry-extension-trace|bcprov-jdk18on|poi-ooxml-' | awk '{print \$4}'"

echo
echo "== RabbitMQ queues =="
sudo -n docker exec btry-rabbitmq-1 rabbitmqctl -q list_queues name messages consumers

echo
echo "== database snapshot (no tokens, keys or buyer PII) =="
sudo -n docker exec -e MYSQL_PWD="$MYSQL_PASSWORD" -i btry-mysql-1 \
  mysql -uroot --batch --raw btry_edu <<'SQL'
SELECT 'orders' AS section;
SELECT id, order_no, status, product_id, product_type, price_cents, currency,
       pay_channel, provider_trade_no, paid_at, fulfilled_at, closed_at, created_at
FROM t_order ORDER BY id;

SELECT 'payment_attempts' AS section;
SELECT id, order_id, attempt_no, channel, status, provider_trade_no, channel_trade_no,
       provider_status, provider_amount_cents, created_at, updated_at
FROM t_payment_attempt ORDER BY id;

SELECT 'payment_events' AS section;
SELECT id, order_id, attempt_id, provider, event_type, process_status,
       error_message, received_at, processed_at
FROM t_payment_event ORDER BY id;

SELECT 'refunds' AS section;
SELECT id, refund_no, order_id, attempt_id, refund_kind, amount_cents, status,
       provider_refund_no, attempt_count, requested_at, succeeded_at, updated_at
FROM t_refund ORDER BY id;

SELECT 'entitlements' AS section;
SELECT id, user_id, product_id, product_type, source, source_order_id, status,
       granted_at, revoked_at, revoke_reason
FROM t_entitlement ORDER BY id;

SELECT 'outbox' AS section;
SELECT id, message_type, order_id, order_no, status, retry_count, max_retry,
       last_error, created_at, updated_at, processed_at
FROM t_outbox_message ORDER BY id;

SELECT 'admin_audit' AS section;
SELECT id, operator_id, action, target_type, target_id, success, duration_ms,
       error_message, trace_id, created_at
FROM t_admin_audit_log ORDER BY id;

SELECT 'alerts' AS section;
SELECT id, alert_type, severity, delivered, delivery_error, created_at
FROM t_alert_log ORDER BY id;

SELECT 'recon_files' AS section;
SELECT id, provider, statement_date, file_name, file_sha256, row_count,
       total_amount_cents, status, parse_error, created_at
FROM t_recon_statement_file ORDER BY id;

SELECT 'recon_runs' AS section;
SELECT id, file_id, run_version, status, total_rows, matched_count, break_count,
       unresolved_break_count, unmatched_amount_cents, started_at, finished_at
FROM t_recon_run ORDER BY id;

SELECT 'recon_matches' AS section;
SELECT id, run_id, item_id, order_id, order_no, attempt_id, match_type, created_at
FROM t_recon_match ORDER BY id;

SELECT 'recon_breaks' AS section;
SELECT id, run_id, item_id, break_type, severity, status, order_no, attempt_id,
       delta_cents, resolution_code, resolved_at, created_at
FROM t_recon_break ORDER BY id;

SELECT 'recon_actions' AS section;
SELECT id, break_id, action_type, from_status, to_status, operator_id, note, created_at
FROM t_recon_action ORDER BY id;
SQL

echo
echo "== payment/outbox/reconciliation metrics =="
sudo -n docker exec btry-backend-1 wget -qO- http://127.0.0.1:9091/actuator/prometheus \
  | grep -E 'btry_payment_|btry_outbox_|btry_recon_' || true

echo
echo "evidence_file=$OUT"
