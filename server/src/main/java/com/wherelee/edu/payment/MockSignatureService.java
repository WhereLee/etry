package com.wherelee.edu.payment;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class MockSignatureService {

    private final PaymentProperties properties;

    public MockSignatureService(PaymentProperties properties) {
        this.properties = properties;
    }

    public String sign(PaymentNotification notification) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.mockSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical(notification).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Mock 支付签名失败", e);
        }
    }

    public boolean verify(PaymentNotification notification) {
        if (notification.signature() == null) {
            return false;
        }
        byte[] expected = sign(notification).getBytes(StandardCharsets.UTF_8);
        byte[] actual = notification.signature().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private String canonical(PaymentNotification notification) {
        return notification.provider() + "|" + notification.eventKey() + "|"
                + notification.orderNo() + "|" + notification.eventType() + "|"
                + notification.amountCents() + "|" + notification.tradeNo();
    }
}
