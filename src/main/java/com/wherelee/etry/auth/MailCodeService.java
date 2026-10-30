package com.wherelee.etry.auth;

import com.wherelee.etry.cache.KeyValueStore;
import com.wherelee.etry.common.BusinessException;
import com.wherelee.etry.common.ErrorCode;
import com.wherelee.etry.config.MailProperties;
import com.wherelee.etry.mail.MailMessage;
import com.wherelee.etry.mail.MailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 验证码的签发与校验。注册和找回密码各自独立的额度与冷却，互不挤占。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailCodeService {

    private static final String CODE_KEY = "etry:mail:code:";
    private static final String FAIL_KEY = "etry:mail:fail:";
    private static final String QUOTA_KEY = "etry:mail:quota:";
    private static final String COOLDOWN_KEY = "etry:mail:cooldown:";

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final KeyValueStore keyValueStore;
    private final MailSender mailSender;
    private final MailProperties mailProperties;
    private final VerifyCodeGenerator codeGenerator;

    public void issue(MailScene scene, String email) {
        MailProperties.Code config = mailProperties.getCode();

        String cooldownKey = COOLDOWN_KEY + scene + ":" + email;
        if (keyValueStore.exists(cooldownKey)) {
            throw new BusinessException(ErrorCode.RESEND_TOO_FREQUENT,
                    "发送过于频繁，请稍后再试");
        }

        String quotaKey = quotaKey(scene, email);
        String usedValue = keyValueStore.get(quotaKey);
        long used = usedValue == null ? 0 : Long.parseLong(usedValue);
        if (used >= config.getDailyLimit()) {
            throw new BusinessException(ErrorCode.DAILY_LIMIT_REACHED);
        }

        String code = codeGenerator.generate(config.getLength());
        keyValueStore.setEx(CODE_KEY + scene + ":" + email, code,
                Duration.ofMinutes(config.getExpireMinutes()));
        keyValueStore.delete(FAIL_KEY + scene + ":" + email);
        keyValueStore.setEx(cooldownKey, "1", Duration.ofSeconds(config.getResendCooldownSeconds()));

        // 只有真正发出去才计入当日额度
        mailSender.send(new MailMessage(email,
                "Etry 邮箱验证码",
                "你的验证码是 " + code + "，" + config.getExpireMinutes() + " 分钟内有效。\n"
                        + "如果不是你本人操作，忽略这封邮件即可。"));
        keyValueStore.incr(quotaKey);
        keyValueStore.expire(quotaKey, untilEndOfDay());
    }

    /**
     * 校验验证码。通过后会删除验证码，同一码不能用两次。
     */
    public void verify(MailScene scene, String email, String code) {
        MailProperties.Code config = mailProperties.getCode();
        String codeKey = CODE_KEY + scene + ":" + email;
        String failKey = FAIL_KEY + scene + ":" + email;

        String expected = keyValueStore.get(codeKey);
        if (expected == null) {
            throw new BusinessException(ErrorCode.CODE_EXPIRED);
        }
        if (!expected.equals(code)) {
            long failures = keyValueStore.incr(failKey);
            keyValueStore.expire(failKey, Duration.ofMinutes(config.getExpireMinutes()));
            if (failures >= config.getMaxAttempts()) {
                keyValueStore.delete(codeKey);
                keyValueStore.delete(failKey);
                throw new BusinessException(ErrorCode.CODE_EXPIRED);
            }
            throw new BusinessException(ErrorCode.CODE_INVALID);
        }
        keyValueStore.delete(codeKey);
        keyValueStore.delete(failKey);
    }

    private String quotaKey(MailScene scene, String email) {
        return QUOTA_KEY + scene + ":" + email + ":" + LocalDate.now().format(DAY_FORMAT);
    }

    private Duration untilEndOfDay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfDay = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, endOfDay);
    }
}
