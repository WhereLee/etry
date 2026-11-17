package com.wherelee.edu.modules.commerce.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单共享 DTO（api 公开契约）。operations 等消费方只依赖 commerce.api，
 * 不再触碰 commerce 内部 dto 包。
 */
public final class OrderDtos {

    private OrderDtos() {
    }

    /**
     * 下单请求。productId/productType 为通用商品引用；seriesId 为 deprecated 别名。
     * 二者至少提供一个，由服务端 resolvedProductId 解析。
     */
    public record CreateOrderRequest(
            Long seriesId,
            Long productId,
            String productType,
            @NotBlank(message = "缺少请求标识") @Size(max = 64, message = "请求标识过长") String clientRequestId
    ) {
        /** 兼容旧客户端：仅传 seriesId。 */
        public CreateOrderRequest(Long seriesId, String clientRequestId) {
            this(seriesId, null, null, clientRequestId);
        }

        public Long resolvedProductId() {
            return productId != null ? productId : seriesId;
        }
    }

    /**
     * 订单视图。productId/productType/productTitle 为通用字段；
     * seriesId/seriesTitle 为 deprecated 别名，过渡期保留以兼容旧前端。
     */
    public record OrderView(
            String orderNo,
            Long productId,
            String productType,
            String productTitle,
            Long seriesId,
            String seriesTitle,
            long priceCents,
            String currency,
            String status,
            String statusLabel,
            String payChannel,
            LocalDateTime expireAt,
            LocalDateTime paidAt,
            LocalDateTime fulfilledAt,
            LocalDateTime createdAt,
            boolean owned
    ) {
    }

    public record PayResponse(String orderNo, String status, String channel, String cashierUrl) {
    }

    public record TimelineItem(String type, String title, LocalDateTime time) {
    }

    public record OrderDetail(OrderView order, List<TimelineItem> timeline) {
    }
}