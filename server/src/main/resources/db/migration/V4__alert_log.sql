CREATE TABLE t_alert_log
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    alert_type    VARCHAR(50)  NOT NULL,
    severity      VARCHAR(20)  NOT NULL,
    message       VARCHAR(1000) NOT NULL,
    delivered     TINYINT(1)   NOT NULL DEFAULT 0,
    delivery_error VARCHAR(500) NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_alert_type_time (alert_type, created_at),
    KEY idx_alert_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='主动告警记录';