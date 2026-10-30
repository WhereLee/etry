package com.wherelee.etry.auth;

import com.wherelee.etry.cache.InMemoryKeyValueStore;
import com.wherelee.etry.common.BusinessException;
import com.wherelee.etry.common.ErrorCode;
import com.wherelee.etry.config.MailProperties;
import com.wherelee.etry.mail.CapturedMailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class MailCodeServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final Pattern CODE_PATTERN = Pattern.compile("(\\d{6})");

    private InMemoryKeyValueStore store;
    private CapturedMailSender mailSender;
    private MailCodeService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryKeyValueStore();
        mailSender = new CapturedMailSender();
        MailProperties properties = new MailProperties();
        properties.setFromAddress("etry@example.com");
        service = new MailCodeService(store, mailSender, properties, new VerifyCodeGenerator());
    }

    @Test
    void correctCodePassesAndCanOnlyBeUsedOnce() {
        service.issue(MailScene.REGISTER, EMAIL);
        String code = lastCode();

        assertThatCode(() -> service.verify(MailScene.REGISTER, EMAIL, code)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.verify(MailScene.REGISTER, EMAIL, code))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CODE_EXPIRED);
    }

    @Test
    void fiveWrongAttemptsInvalidateTheCode() {
        service.issue(MailScene.REGISTER, EMAIL);
        String code = lastCode();
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> service.verify(MailScene.REGISTER, EMAIL, wrong))
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CODE_INVALID);
        }
        assertThatThrownBy(() -> service.verify(MailScene.REGISTER, EMAIL, wrong))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CODE_EXPIRED);

        // 验证码已作废，即使填对也不行
        assertThatThrownBy(() -> service.verify(MailScene.REGISTER, EMAIL, code))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CODE_EXPIRED);
    }

    @Test
    void resendWithinCooldownIsRejected() {
        service.issue(MailScene.REGISTER, EMAIL);

        assertThatThrownBy(() -> service.issue(MailScene.REGISTER, EMAIL))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESEND_TOO_FREQUENT);
    }

    @Test
    void registerAndResetUseSeparateQuota() {
        service.issue(MailScene.REGISTER, EMAIL);
        service.issue(MailScene.RESET, EMAIL);

        assertThat(mailSender.recent()).hasSize(2);
    }

    @Test
    void dailyLimitBlocksSixthMailForSameScene() {
        for (int i = 0; i < 5; i++) {
            store.delete("etry:mail:cooldown:" + MailScene.REGISTER + ":" + EMAIL);
            service.issue(MailScene.REGISTER, EMAIL);
        }
        store.delete("etry:mail:cooldown:" + MailScene.REGISTER + ":" + EMAIL);

        assertThatThrownBy(() -> service.issue(MailScene.REGISTER, EMAIL))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DAILY_LIMIT_REACHED);
    }

    private String lastCode() {
        String body = mailSender.recent().get(0).body();
        Matcher matcher = CODE_PATTERN.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
