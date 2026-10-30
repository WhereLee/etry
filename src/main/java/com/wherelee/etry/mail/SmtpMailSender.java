package com.wherelee.etry.mail;

import com.wherelee.etry.config.MailProperties;
import com.wherelee.etry.common.BusinessException;
import com.wherelee.etry.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * QQ 邮箱 SMTP 发信。端口 465 + SSL，密码是 16 位授权码而不是 QQ 密码。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "etry.mail.type", havingValue = "smtp")
public class SmtpMailSender implements MailSender {

    private final MailProperties properties;
    private final JavaMailSenderImpl mailSender;

    public SmtpMailSender(MailProperties properties) {
        this.properties = properties;
        this.mailSender = new JavaMailSenderImpl();
        mailSender.setHost(properties.getSmtp().getHost());
        mailSender.setPort(properties.getSmtp().getPort());
        mailSender.setUsername(properties.getSmtp().getUsername());
        mailSender.setPassword(properties.getSmtp().getPassword());
        mailSender.setDefaultEncoding("UTF-8");

        Properties javaMail = mailSender.getJavaMailProperties();
        javaMail.put("mail.smtp.auth", "true");
        javaMail.put("mail.smtp.ssl.enable", "true");
        javaMail.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        javaMail.put("mail.smtp.connectiontimeout", "10000");
        javaMail.put("mail.smtp.timeout", "10000");
        javaMail.put("mail.smtp.writetimeout", "10000");
    }

    @Override
    public void send(MailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.getFromAddress());
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        try {
            mailSender.send(mail);
            log.info("邮件已发送 to={} subject={}", message.to(), message.subject());
        } catch (MailException e) {
            log.error("邮件发送失败 to={}", message.to(), e);
            throw new BusinessException(ErrorCode.MAIL_SEND_FAILED);
        }
    }
}
