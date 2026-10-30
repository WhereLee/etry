package com.wherelee.trade.product.dto;

import com.wherelee.trade.product.Product;

public record ProductVO(
        Long id,
        String name,
        String description,
        String category,
        Long pricePoints,
        Integer availableStock,
        String status
) {

    public static ProductVO from(Product product) {
        return new ProductVO(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.getPricePoints(),
                product.availableStock(),
                product.getStatus());
    }
}
