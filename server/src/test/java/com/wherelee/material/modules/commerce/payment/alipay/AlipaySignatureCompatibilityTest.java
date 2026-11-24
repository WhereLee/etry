package com.wherelee.material.modules.commerce.payment.alipay;

import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class AlipaySignatureCompatibilityTest {

    @Test
    void pagePaySignsWithBouncyCastleOnClasspathWithoutCallingAlipay() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        AlipayConfig config = new AlipayConfig();
        config.setServerUrl("https://openapi.alipay.com/gateway.do");
        config.setAppId("2021000000000000");
        config.setPrivateKey(privateKey);
        config.setAlipayPublicKey(publicKey);
        config.setFormat("json");
        config.setCharset("UTF-8");
        config.setSignType("RSA2");
        AlipayClient client = new DefaultAlipayClient(config);

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl("https://example.com/api/payments/alipay/notify");
        request.setReturnUrl("https://example.com/learn/orders/TEST");
        request.setBizContent("""
                {"out_trade_no":"TEST-1","total_amount":"0.01","subject":"signature test",
                 "product_code":"FAST_INSTANT_TRADE_PAY"}
                """);

        AlipayTradePagePayResponse response = client.pageExecute(request, "GET");

        assertThat(new BouncyCastleProvider().getName()).isEqualTo(BouncyCastleProvider.PROVIDER_NAME);
        assertThat(response.getBody())
                .contains("openapi.alipay.com")
                .contains("sign=")
                .contains("TEST-1");
    }
}
