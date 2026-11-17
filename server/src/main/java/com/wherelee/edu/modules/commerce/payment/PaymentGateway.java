package com.wherelee.edu.modules.commerce.payment;

import com.wherelee.edu.modules.commerce.order.OrderRecord;

public interface PaymentGateway {

    String channel();

    /**
     * 由渠道生成商户侧支付单号，保证同一订单同一尝试幂等。
     */
    String outTradeNo(OrderRecord order, int attemptNo);

    PaymentAction initiate(OrderRecord order, PaymentAttemptRecord attempt);

    PaymentQueryResult query(OrderRecord order, PaymentAttemptRecord attempt);

    /**
     * 关闭旧收银台交易。只有明确关闭成功或交易不存在才返回 CLOSED/NOT_FOUND；
     * 网络异常、超时、未知返回一律 PENDING，由补偿任务重试，不能臆断关闭成功。
     */
    CloseResult close(OrderRecord order, PaymentAttemptRecord attempt);

    RefundResult refund(OrderRecord order, PaymentAttemptRecord attempt,
                        RefundRecord refund, boolean simulateFailure);

    record PaymentAction(String cashierUrl) {
    }

    record PaymentQueryResult(String status, long amountCents, String tradeNo, String message) {
    }

    record CloseResult(String status, String message) {
    }

    record RefundResult(boolean success, String providerRefundNo, String message) {
    }
}
