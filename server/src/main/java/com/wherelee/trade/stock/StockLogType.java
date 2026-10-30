package com.wherelee.trade.stock;

/**
 * 库存流水类型。M0 只写 RESERVE，其余为后续阶段预留。
 */
public enum StockLogType {

    /** 下单预占，冻结增加 */
    RESERVE,
    /** 订单取消，冻结释放 */
    RELEASE,
    /** 支付完成，冻结转实际扣减 */
    DEDUCT,
    /** 退款，库存回补 */
    RETURN
}
