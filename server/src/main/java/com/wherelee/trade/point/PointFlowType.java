package com.wherelee.trade.point;

/**
 * 积分流水类型。M0 只用到 RECHARGE 与 FREEZE，其余为后续阶段预留。
 */
public enum PointFlowType {

    /** 充值到账，可用余额增加 */
    RECHARGE,
    /** 下单冻结，可用减少、冻结增加 */
    FREEZE,
    /** 支付完成，冻结转扣减 */
    DEDUCT,
    /** 订单取消，冻结退回可用 */
    UNFREEZE,
    /** 退款，积分退回可用 */
    REFUND
}
