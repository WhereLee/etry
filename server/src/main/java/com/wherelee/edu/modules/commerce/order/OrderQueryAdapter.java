package com.wherelee.edu.modules.commerce.order;

import com.wherelee.edu.modules.commerce.api.OrderQuery;
import com.wherelee.edu.modules.entitlement.api.ProductTypes;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 订单交易事实读适配器：把仓储的进行中订单查询翻译为 api 级事实记录，
 * 供跨模块读模型门面叠加，不泄漏 OrderRecord 内部结构。
 */
@Component
public class OrderQueryAdapter implements OrderQuery {

    private final OrderRepository orderRepository;

    public OrderQueryAdapter(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public Optional<ActiveOrderFact> activeOrder(Long userId, Long productId, String productType) {
        if (!ProductTypes.COURSE_SERIES.equals(productType)) {
            return Optional.empty();
        }
        return orderRepository.findActive(userId, productId)
                .map(order -> new ActiveOrderFact(order.orderNo(), order.status().name()));
    }
}