package com.wherelee.edu.payment.alipay;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 支付宝金额单位是「元」的两位小数字符串，系统内部统一用「分」。
 * 这里集中做换算，避免各处散落浮点运算。
 */
public final class AlipayAmounts {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private AlipayAmounts() {
    }

    public static String toYuan(long cents) {
        if (cents < 0) {
            throw new IllegalArgumentException("金额不能为负数：" + cents);
        }
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }

    public static long toCents(String yuan) {
        if (yuan == null || yuan.isBlank()) {
            throw new IllegalArgumentException("支付宝金额缺失");
        }
        return new BigDecimal(yuan.trim()).multiply(HUNDRED)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
