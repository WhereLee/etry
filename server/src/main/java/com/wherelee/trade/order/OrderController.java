package com.wherelee.trade.order;

import com.wherelee.trade.auth.UserContext;
import com.wherelee.trade.common.ApiResponse;
import com.wherelee.trade.common.PageResult;
import com.wherelee.trade.order.dto.CreateOrderRequest;
import com.wherelee.trade.order.dto.OrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ApiResponse<OrderVO> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                       @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok(orderService.create(UserContext.requireUserId(), idempotencyKey, request));
    }

    @GetMapping
    public ApiResponse<PageResult<OrderVO>> page(@RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(orderService.page(UserContext.requireUserId(), status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.detail(UserContext.requireUserId(), id));
    }
}
