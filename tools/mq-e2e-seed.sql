USE btry_edu;

DELETE FROM t_outbox_message WHERE order_no IN ('MQE2E-20261124-OK', 'MQE2E-20261124-FAIL');
DELETE FROM t_entitlement WHERE source_order_id IN (
    SELECT id FROM t_order WHERE order_no IN ('MQE2E-20261124-OK', 'MQE2E-20261124-FAIL')
);
DELETE FROM t_order WHERE order_no IN ('MQE2E-20261124-OK', 'MQE2E-20261124-FAIL');

INSERT INTO t_order (order_no, user_id, series_id, series_title_snapshot, product_id, product_type,
                     product_title_snapshot, price_cents, currency, status, pay_channel,
                     client_request_id, active_key, expire_at, paid_at)
VALUES ('MQE2E-20261124-OK', 3, 1, 'MQ E2E OK', 1, 'COURSE_SERIES', 'MQ E2E OK', 0, 'CNY',
        'PAID', 'ALIPAY', 'mqe2e-ok-20261124', NULL, DATE_ADD(NOW(3), INTERVAL 30 MINUTE), NOW(3));
SET @ok_id = LAST_INSERT_ID();

INSERT INTO t_order (order_no, user_id, series_id, series_title_snapshot, product_id, product_type,
                     product_title_snapshot, price_cents, currency, status, pay_channel,
                     client_request_id, active_key, expire_at)
VALUES ('MQE2E-20261124-FAIL', 3, 2, 'MQ E2E FAIL', 2, 'COURSE_SERIES', 'MQ E2E FAIL', 0, 'CNY',
        'CREATED', 'ALIPAY', 'mqe2e-fail-20261124', NULL, DATE_ADD(NOW(3), INTERVAL 30 MINUTE));
SET @fail_id = LAST_INSERT_ID();

INSERT INTO t_outbox_message (message_type, order_id, order_no, user_id, product_id, product_type,
                              reason, status, max_retry)
VALUES ('FULFILL', @ok_id, 'MQE2E-20261124-OK', 3, 1, 'COURSE_SERIES', NULL, 'NEW', 5),
       ('FULFILL', @fail_id, 'MQE2E-20261124-FAIL', 3, 2, 'COURSE_SERIES', NULL, 'NEW', 5);

SELECT id, order_no, status FROM t_order WHERE order_no LIKE 'MQE2E-20261124-%';
SELECT id, message_type, order_no, status FROM t_outbox_message WHERE order_no LIKE 'MQE2E-20261124-%';
