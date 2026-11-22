package com.wherelee.material.modules.commerce.cart;

import com.wherelee.material.modules.commerce.api.OrderDtos;
import com.wherelee.material.modules.platform.aop.Measured;
import com.wherelee.material.modules.platform.aop.RateLimit;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;
    private final CartCheckoutService cartCheckoutService;

    public CartController(CartService cartService, CartCheckoutService cartCheckoutService) {
        this.cartService = cartService;
        this.cartCheckoutService = cartCheckoutService;
    }

    @GetMapping
    public CartDtos.CartView list(@RequestAttribute("userId") Long userId) {
        return cartService.list(userId);
    }

    @PostMapping
    public void add(@RequestAttribute("userId") Long userId,
                    @Valid @RequestBody CartDtos.AddRequest request) {
        cartService.add(userId, request.materialId());
    }

    @PostMapping("/checkout")
    @RateLimit(key = "order:create", limit = 20, windowSeconds = 60)
    @Measured("cart.checkout")
    public OrderDtos.OrderView checkout(@RequestAttribute("userId") Long userId,
                                        @Valid @RequestBody CartDtos.CheckoutRequest request) {
        return cartCheckoutService.checkout(userId, request);
    }

    @DeleteMapping("/{materialId}")
    public void remove(@RequestAttribute("userId") Long userId, @PathVariable Long materialId) {
        cartService.remove(userId, materialId);
    }
}