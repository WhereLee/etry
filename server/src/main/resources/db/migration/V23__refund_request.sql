-- 阶段6 买家退款申请（PRD §2 买家「申请整单退款」、客服「处理退款申请」）。
-- 每单一条申请记录：PENDING 处理中 / APPROVED 已同意 / REJECTED 已拒绝；拒绝后可再次申请（重置为 PENDING）。
CREATE TABLE t_refund_request (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT       NOT NULL COMMENT '订单ID',
    order_no    VARCHAR(40)  NOT NULL COMMENT '订单号快照',
    user_id     BIGINT       NOT NULL COMMENT '申请人',
    reason      VARCHAR(500) NOT NULL COMMENT '申请理由',
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    handled_by  BIGINT       NULL COMMENT '处理人',
    handled_at  DATETIME     NULL COMMENT '处理时间',
    handle_note VARCHAR(500) NULL COMMENT '处理备注（拒绝理由）',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近申请时间',
    UNIQUE KEY uk_refund_request_order (order_id),
    KEY idx_refund_request_status (status, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='买家整单退款申请';