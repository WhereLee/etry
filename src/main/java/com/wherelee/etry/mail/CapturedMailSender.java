package com.wherelee.etry.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 开发与测试用，不真正发信，只把邮件写进内存并打日志。
 * 测试代码可以直接从这里取出验证码。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "etry.mail.type", havingValue = "captured", matchIfMissing = true)
public class CapturedMailSender implements MailSender {

    private static final int MAX_KEEP = 50;

    public record CapturedMail(LocalDateTime sentAt, String to, String subject, String body) {
    }

    private final Deque<CapturedMail> mailbox = new ArrayDeque<>();

    @Override
    public synchronized void send(MailMessage message) {
        CapturedMail mail = new CapturedMail(LocalDateTime.now(), message.to(), message.subject(), message.body());
        mailbox.addFirst(mail);
        while (mailbox.size() > MAX_KEEP) {
            mailbox.removeLast();
        }
        log.info("[captured-mail] to={} subject={} body={}", message.to(), message.subject(), message.body());
    }

    public synchronized List<CapturedMail> recent() {
        return new ArrayList<>(mailbox);
    }

    public synchronized void clear() {
        mailbox.clear();
    }
}
