package com.wherelee.edu.payment.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝客户端装配。只在 provider=alipay 时生效，缺关键配置直接启动失败，
 * 避免带着空密钥上线后在支付环节才暴露问题。
 */
@Configuration
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "alipay")
public class AlipayClientConfig {

    @Bean
    public AlipayClient alipayClient(AlipayProperties properties) throws AlipayApiException {
        require(properties.appId(), "ALIPAY_APP_ID");
        require(properties.privateKey(), "ALIPAY_PRIVATE_KEY");
        require(properties.alipayPublicKey(), "ALIPAY_PUBLIC_KEY");
        require(properties.gatewayUrl(), "ALIPAY_GATEWAY_URL");
        require(properties.notifyUrl(), "ALIPAY_NOTIFY_URL");
        require(properties.returnUrl(), "ALIPAY_RETURN_URL");

        AlipayConfig config = new AlipayConfig();
        config.setServerUrl(properties.gatewayUrl());
        config.setAppId(properties.appId());
        config.setPrivateKey(properties.privateKey());
        config.setAlipayPublicKey(properties.alipayPublicKey());
        config.setFormat(properties.format());
        config.setCharset(properties.charset());
        config.setSignType(properties.signType());
        config.setConnectTimeout(properties.connectTimeoutMs());
        config.setReadTimeout(properties.readTimeoutMs());
        return new DefaultAlipayClient(config);
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("支付宝支付缺少必要配置：" + name);
        }
    }
}
