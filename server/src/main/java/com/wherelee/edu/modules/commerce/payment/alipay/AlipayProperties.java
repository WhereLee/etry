package com.wherelee.edu.modules.commerce.payment.alipay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付宝电脑网站支付配置（公钥模式）。
 * 私钥与支付宝公钥只从环境变量注入，不写入仓库。
 */
@ConfigurationProperties(prefix = "app.payment.alipay")
public record AlipayProperties(
        String appId,
        String privateKey,
        String alipayPublicKey,
        String sellerId,
        String gatewayUrl,
        String notifyUrl,
        String returnUrl,
        String charset,
        String signType,
        String format,
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
