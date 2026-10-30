package com.wherelee.etry.mail;

/**
 * 发信抽象。生产走 QQ 邮箱 SMTP，开发和测试写内存，
 * 保证跑测试不会真的往外发邮件、也不会消耗日发信额度。
 */
public interface MailSender {

    void send(MailMessage message);
}
