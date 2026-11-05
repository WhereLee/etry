package com.wherelee.edu.modules.commerce.api;

import com.wherelee.edu.modules.commerce.order.dto.OrderDtos;
import com.wherelee.edu.modules.commerce.payment.PaymentDtos;

import java.util.List;

/**
 * 运营端订单操作门面。
 *
 * <p>operations.admin 只依赖这个应用契约，不直接访问订单/支付/退款 Repository，
 * 也不自行编排履约与补偿流程。</p>
 */
public interface AdminOrderOperations {

    Snapshot snapshot(String orderNo);

    void queryNow(String orderNo);

    void fulfill(String orderNo);

    void close(String orderNo);

    void refund(String orderNo, Long operatorId, String reason, boolean simulateFailure);

    record Snapshot(
            OrderDtos.OrderDetail orderDetail,
            List<PaymentDtos.AttemptView> attempts,
            List<PaymentDtos.EventView> events,
            PaymentDtos.RefundView refund
    ) {
    }
}