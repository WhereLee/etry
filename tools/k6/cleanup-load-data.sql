DELETE FROM t_payment_event
WHERE order_id IN (SELECT id FROM t_order WHERE series_id BETWEEN 9001 AND 9010);
DELETE FROM t_payment_attempt
WHERE order_id IN (SELECT id FROM t_order WHERE series_id BETWEEN 9001 AND 9010);
DELETE FROM t_refund
WHERE order_id IN (SELECT id FROM t_order WHERE series_id BETWEEN 9001 AND 9010);
DELETE FROM t_student_series WHERE series_id BETWEEN 9001 AND 9010;
DELETE FROM t_order WHERE series_id BETWEEN 9001 AND 9010;
DELETE FROM t_series WHERE id BETWEEN 9001 AND 9010;
