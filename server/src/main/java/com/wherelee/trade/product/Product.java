package com.wherelee.trade.product;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Product {

    public static final String STATUS_ON_SALE = "ON_SALE";
    public static final String STATUS_OFF_SHELF = "OFF_SHELF";

    private Long id;
    private String name;
    private String description;
    private String category;
    private Long pricePoints;
    private Integer stockTotal;
    private Integer stockFrozen;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 可售数量不落库，由总量减冻结算出来，避免三个数互相不一致。
     */
    public int availableStock() {
        if (stockTotal == null || stockFrozen == null) {
            return 0;
        }
        return stockTotal - stockFrozen;
    }
}
