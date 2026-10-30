package com.wherelee.trade.order;

/**
 * 订单状态。M0 只有 PENDING_PAY 会被写入，其余为后续阶段预留，
 * 提前定义是为了让状态机在代码里有一处唯一定义。
 */
public final class OrderStatus {

    /** 已创建，等待支付 */
    public static final String PENDING_PAY = "PENDING_PAY";
    /** 已支付 */
    public static final String PAID = "PAID";
    /** 已取消（超时或用户主动） */
    public static final String CANCELLED = "CANCELLED";
    /** 已发货 */
    public static final String SHIPPED = "SHIPPED";
    /** 已完成 */
    public static final String COMPLETED = "COMPLETED";
    /** 退款中 */
    public static final String REFUNDING = "REFUNDING";
    /** 已退款 */
    public static final String REFUNDED = "REFUNDED";

    private OrderStatus() {
    }
}
