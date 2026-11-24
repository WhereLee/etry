package com.wherelee.material.modules.commerce.payment;

import com.wherelee.material.modules.commerce.order.OrderRecord;
import com.wherelee.material.modules.commerce.order.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 支付发起的事务边界：只做「订单置 PAYING + 创建 attempt」的数据库事务，
 * 渠道下单（网络调用）由 PaymentService 在事务提交后执行，避免长事务持锁跨网络。
 */
@Service
public class PaymentAttemptStarter {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    public PaymentAttemptStarter(OrderRepository orderRepository,
                                 PaymentRepository paymentRepository,
                                 PaymentGateway paymentGateway) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<Started> start(OrderRecord order) {
        int changed = orderRepository.markPaying(order.orderNo(), paymentGateway.channel());
        if (changed == 0) {
            return Optional.empty();
        }
        int attemptNo = paymentRepository.nextAttemptNo(order.id());
        String tradeNo = paymentGateway.outTradeNo(order, attemptNo);
        paymentRepository.createAttempt(
                order.id(), attemptNo, paymentGateway.channel(), tradeNo, order.priceCents(),
                PaymentDigests.sha256Hex(order.orderNo() + "|" + attemptNo + "|" + order.priceCents()));
        PaymentAttemptRecord attempt = paymentRepository.findLatestAttempt(order.id())
                .orElseThrow(() -> new IllegalStateException("支付尝试创建后不可读取"));
        return Optional.of(new Started(order, attempt));
    }

    public record Started(OrderRecord order, PaymentAttemptRecord attempt) {
    }
}
