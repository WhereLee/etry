package com.wherelee.trade.order;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单号：17 位时间戳（毫秒）+ 4 位随机数。
 * 唯一性由 t_order.uk_order_no 兜底，这里只负责可读与低碰撞概率。
 * 后续若需严格单调，可换成号段或雪花算法。
 */
public final class OrderNoGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private OrderNoGenerator() {
    }

    public static String next() {
        return LocalDateTime.now().format(FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10_000));
    }
}
