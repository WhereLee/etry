package com.wherelee.etry.mail;

import com.wherelee.etry.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 本地开发用的“邮箱”，把最近发出的邮件和验证码直接列出来。
 * 生产环境（etry.mail.type=smtp）不会注册这个端点。
 */
@RestController
@RequestMapping("/api/dev/mailbox")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "etry.mail.type", havingValue = "captured", matchIfMissing = true)
public class DevMailboxController {

    private final CapturedMailSender capturedMailSender;

    @GetMapping
    public ApiResponse<List<CapturedMailSender.CapturedMail>> recent() {
        return ApiResponse.ok(capturedMailSender.recent());
    }
}
