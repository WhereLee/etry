package com.wherelee.edu.modules.commerce.payment.alipay;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 支付宝异步通知入口。公开路径（在 JwtAuthFilter 白名单内），不加登录态、不限流，
 * 否则支付宝重投会被 401/429 挡住。响应体必须是纯文本 success / failure。
 */
@RestController
@RequestMapping("/api/payments/alipay")
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "alipay")
public class AlipayNotifyController {

    private static final int MAX_NOTIFY_BYTES = 64 * 1024;

    private final AlipayNotifyService notifyService;

    public AlipayNotifyController(AlipayNotifyService notifyService) {
        this.notifyService = notifyService;
    }

    @PostMapping(value = "/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notify(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_NOTIFY_BYTES) {
            return "failure";
        }
        byte[] body = readBounded(request.getInputStream());
        if (body == null) {
            return "failure";
        }
        return notifyService.handle(body);
    }

    private byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(8192);
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_NOTIFY_BYTES) {
                return null;
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
