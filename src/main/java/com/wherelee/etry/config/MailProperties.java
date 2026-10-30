package com.wherelee.etry.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "etry.mail")
public class MailProperties {

    /** smtp 或 captured */
    private String type = "captured";

    /** 发件人地址，QQ 邮箱下与账号一致 */
    private String fromAddress;

    /**
     * 注册是否需要邮箱验证，默认开启。
     * 关掉后注册直接生效、不发验证码；找回密码仍然走邮箱验证码。
     */
    private boolean verifyRegister = true;

    private Smtp smtp = new Smtp();

    private Code code = new Code();

    @Data
    public static class Smtp {
        private String host = "smtp.qq.com";
        private int port = 465;
        private String username;
        private String password;
    }

    @Data
    public static class Code {
        private int length = 6;
        private int expireMinutes = 15;
        private int maxAttempts = 5;
        private int resendCooldownSeconds = 60;
        private int dailyLimit = 5;
    }
}
