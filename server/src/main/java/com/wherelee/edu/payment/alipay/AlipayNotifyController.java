package com.wherelee.edu.payment.alipay;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 支付宝异步通知入口。公开路径（在 JwtAuthFilter 白名单内），不加登录态、不限流，
 * 否则支付宝重投会被 401/429 挡住。响应体必须是纯文本 success / failure。
 */
@RestController
@RequestMapping("/api/payments/alipay")
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "alipay")
public class AlipayNotifyController {

    private final AlipayNotifyService notifyService;

    public AlipayNotifyController(AlipayNotifyService notifyService) {
        this.notifyService = notifyService;
    }

    @PostMapping(value = "/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notify(HttpServletRequest request) throws IOException {
        return notifyService.handle(request.getInputStream().readAllBytes());
    }
}
