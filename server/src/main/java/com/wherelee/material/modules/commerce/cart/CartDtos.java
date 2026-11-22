package com.wherelee.material.modules.commerce.cart;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 购物车接口契约。onSale/purchased 由服务端叠加，前端据此置灰结算项。
 */
public final class CartDtos {

    public record AddRequest(@NotNull(message = "缺少资料ID") @Positive Long materialId) {
    }

    public record CheckoutRequest(
            @NotEmpty(message = "请选择要结算的资料") List<@NotNull @Positive Long> materialIds,
            @NotBlank(message = "缺少请求标识") @Size(max = 64, message = "请求标识过长") String clientRequestId
    ) {
    }

    public record ItemView(
            Long materialId,
            String title,
            String category,
            long priceCents,
            String currency,
            String coverKey,
            boolean onSale,
            boolean purchased
    ) {
    }

    public record CartView(List<ItemView> items, long totalCents) {
    }

    private CartDtos() {
    }
}