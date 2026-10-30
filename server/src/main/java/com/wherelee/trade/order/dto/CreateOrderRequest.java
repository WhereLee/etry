package com.wherelee.trade.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(

        @NotEmpty(message = "订单项不能为空")
        @Size(max = 20, message = "单次下单商品种类不能超过 20")
        @Valid
        List<Item> items,

        @Size(max = 200, message = "收货地址不能超过 200 字")
        String address
) {

    public record Item(
            @NotNull(message = "商品 ID 不能为空")
            Long productId,

            @NotNull(message = "数量不能为空")
            @Min(value = 1, message = "数量至少为 1")
            @Max(value = 99, message = "单个商品数量不能超过 99")
            Integer quantity
    ) {
    }
}
