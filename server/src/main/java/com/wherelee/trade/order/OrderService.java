package com.wherelee.trade.order;

import com.wherelee.trade.common.BizException;
import com.wherelee.trade.common.ErrorCode;
import com.wherelee.trade.common.PageResult;
import com.wherelee.trade.order.dto.CreateOrderRequest;
import com.wherelee.trade.order.dto.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 64;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderTxService orderTxService;

    /**
     * 幂等入口：同一用户 + 同一幂等键只会产生一张订单。
     * 正常路径靠预检查，并发穿透时靠唯一索引兜底。
     */
    public OrderVO create(Long userId, String idempotencyKey, CreateOrderRequest request) {
        validateIdempotencyKey(idempotencyKey);

        Order existing = orderMapper.findByUserAndIdempotencyKey(userId, idempotencyKey);
        if (existing != null) {
            return toVO(existing);
        }

        try {
            return toVO(orderTxService.create(userId, idempotencyKey, request));
        } catch (DuplicateKeyException e) {
            // 两个请求同时通过预检查，唯一索引拦下其中一个；事务已回滚，此处重新读取即可
            Order concurrent = orderMapper.findByUserAndIdempotencyKey(userId, idempotencyKey);
            if (concurrent == null) {
                throw e;
            }
            return toVO(concurrent);
        }
    }

    public PageResult<OrderVO> page(Long userId, String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = orderMapper.countByUser(userId, status);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<Order> orders = orderMapper.findPageByUser(userId, status, (safePage - 1) * safeSize, safeSize);
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        List<OrderItem> allItems = orderItemMapper.findByOrderIds(orderIds);

        List<OrderVO> records = orders.stream()
                .map(order -> OrderVO.of(order, allItems.stream()
                        .filter(item -> item.getOrderId().equals(order.getId()))
                        .toList()))
                .toList();
        return new PageResult<>(records, total, safePage, safeSize);
    }

    public OrderVO detail(Long userId, Long orderId) {
        Order order = orderMapper.findById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        return toVO(order);
    }

    private OrderVO toVO(Order order) {
        return OrderVO.of(order, orderItemMapper.findByOrderId(order.getId()));
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "缺少 Idempotency-Key 请求头");
        }
        if (idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new BizException(ErrorCode.INVALID_PARAM,
                    "Idempotency-Key 长度不能超过 " + IDEMPOTENCY_KEY_MAX_LENGTH);
        }
    }
}
