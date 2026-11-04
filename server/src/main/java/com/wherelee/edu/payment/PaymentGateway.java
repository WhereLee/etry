package com.wherelee.edu.payment;

import com.wherelee.edu.order.OrderRecord;

public interface PaymentGateway {

    String channel();

    /**
     * 由渠道生成商户侧支付单号，保证同一订单同一尝试幂等。
     */
    String outTradeNo(OrderRecord order, int attemptNo);

    PaymentAction initiate(OrderRecord order, PaymentAttemptRecord attempt);

    PaymentQueryResult query(OrderRecord order, PaymentAttemptRecord attempt);

    RefundResult refund(OrderRecord order, RefundRecord refund, boolean simulateFailure);

    record PaymentAction(String cashierUrl) {
    }

    record PaymentQueryResult(String status, long amountCents, String tradeNo, String message) {
    }

    record RefundResult(boolean success, String providerRefundNo, String message) {
    }
}
