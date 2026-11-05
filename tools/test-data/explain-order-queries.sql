-- Run against a disposable performance schema after generate_perf_data.py.
SELECT '1. user order page' AS scenario;
EXPLAIN ANALYZE
SELECT * FROM t_order
WHERE user_id = 101
ORDER BY created_at DESC, id DESC
LIMIT 20 OFFSET 0;

SELECT '2. active order lookup' AS scenario;
EXPLAIN ANALYZE
SELECT * FROM t_order
WHERE user_id = 101 AND series_id = 1
  AND status IN ('CREATED', 'PAYING', 'PAY_FAILED', 'PAID')
ORDER BY id DESC LIMIT 1;

SELECT '3. pending query' AS scenario;
EXPLAIN ANALYZE
SELECT * FROM t_order
WHERE status = 'PAYING'
  AND updated_at <= TIMESTAMPADD(SECOND, -15, CURRENT_TIMESTAMP(3))
ORDER BY updated_at, id
LIMIT 50;

SELECT '4. expired orders' AS scenario;
EXPLAIN ANALYZE
SELECT * FROM t_order
WHERE status IN ('CREATED', 'PAY_FAILED')
  AND expire_at <= CURRENT_TIMESTAMP(3)
ORDER BY expire_at, id
LIMIT 50;

SELECT '5. paid unfulfilled' AS scenario;
EXPLAIN ANALYZE
SELECT * FROM t_order
WHERE status = 'PAID'
ORDER BY paid_at, id
LIMIT 50;

SELECT '6. latest payment attempt' AS scenario;
EXPLAIN ANALYZE
SELECT * FROM t_payment_attempt
WHERE order_id = 1
ORDER BY attempt_no DESC LIMIT 1;

SELECT '7. pending refunds' AS scenario;
EXPLAIN ANALYZE
SELECT * FROM t_refund
WHERE status = 'PROCESSING'
  AND updated_at <= TIMESTAMPADD(SECOND, -15, CURRENT_TIMESTAMP(3))
ORDER BY updated_at, id
LIMIT 50;

SELECT '8. retryable refunds' AS scenario;
EXPLAIN ANALYZE
SELECT * FROM t_refund
WHERE status = 'FAILED'
  AND attempt_count < 5
  AND next_retry_at IS NOT NULL
  AND next_retry_at <= CURRENT_TIMESTAMP(3)
ORDER BY next_retry_at, id
LIMIT 50;

SELECT '9. payment event idempotency' AS scenario;
EXPLAIN ANALYZE
SELECT * FROM t_payment_event
WHERE provider = 'MOCK' AND event_key = 'PERF-EV-1-1';

SELECT '10. playback by user and time' AS scenario;
EXPLAIN ANALYZE
SELECT * FROM t_playback_log
WHERE user_id = 101
ORDER BY created_at DESC
LIMIT 50;

SELECT '11. playback by video and time' AS scenario;
EXPLAIN ANALYZE
SELECT * FROM t_playback_log
WHERE video_id = 1
ORDER BY created_at DESC
LIMIT 50;

SELECT '12. admin audit by operator and time' AS scenario;
EXPLAIN ANALYZE
SELECT * FROM t_admin_audit_log
WHERE operator_id = 1
ORDER BY created_at DESC
LIMIT 50;
