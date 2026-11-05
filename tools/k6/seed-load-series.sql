SET @teacher_id = (SELECT id FROM t_user WHERE username = 'teacher' LIMIT 1);

INSERT INTO t_series
    (id, teacher_id, title, summary, sort, status, published_at,
     sale_status, price_cents, currency, sale_updated_at)
VALUES
    (9001, @teacher_id, 'LOAD-01', 'k6 load series', 9001, 'PUBLISHED', NOW(3), 'ON_SALE', 199, 'CNY', NOW(3)),
    (9002, @teacher_id, 'LOAD-02', 'k6 load series', 9002, 'PUBLISHED', NOW(3), 'ON_SALE', 199, 'CNY', NOW(3)),
    (9003, @teacher_id, 'LOAD-03', 'k6 load series', 9003, 'PUBLISHED', NOW(3), 'ON_SALE', 199, 'CNY', NOW(3)),
    (9004, @teacher_id, 'LOAD-04', 'k6 load series', 9004, 'PUBLISHED', NOW(3), 'ON_SALE', 199, 'CNY', NOW(3)),
    (9005, @teacher_id, 'LOAD-05', 'k6 load series', 9005, 'PUBLISHED', NOW(3), 'ON_SALE', 199, 'CNY', NOW(3)),
    (9006, @teacher_id, 'LOAD-06', 'k6 load series', 9006, 'PUBLISHED', NOW(3), 'ON_SALE', 199, 'CNY', NOW(3)),
    (9007, @teacher_id, 'LOAD-07', 'k6 load series', 9007, 'PUBLISHED', NOW(3), 'ON_SALE', 199, 'CNY', NOW(3)),
    (9008, @teacher_id, 'LOAD-08', 'k6 load series', 9008, 'PUBLISHED', NOW(3), 'ON_SALE', 199, 'CNY', NOW(3)),
    (9009, @teacher_id, 'LOAD-09', 'k6 load series', 9009, 'PUBLISHED', NOW(3), 'ON_SALE', 199, 'CNY', NOW(3)),
    (9010, @teacher_id, 'LOAD-10', 'k6 load series', 9010, 'PUBLISHED', NOW(3), 'ON_SALE', 199, 'CNY', NOW(3))
ON DUPLICATE KEY UPDATE sale_status = 'ON_SALE', price_cents = 199, status = 'PUBLISHED';
