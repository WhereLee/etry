package com.wherelee.material.modules.commerce.cart;

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

    public CartController(CartService cartService) {
        this.cartService = cartService;
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

    @DeleteMapping("/{materialId}")
    public void remove(@RequestAttribute("userId") Long userId, @PathVariable Long materialId) {
        cartService.remove(userId, materialId);
    }
}