package com.wherelee.etry.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定 etry 下的配额、上传、头像、回收站、分享、安全、定时任务参数。
 */
@Data
@ConfigurationProperties(prefix = "etry")
public class EtryProperties {

    private Quota quota = new Quota();
    private Upload upload = new Upload();
    private Avatar avatar = new Avatar();
    private Trash trash = new Trash();
    private Share share = new Share();
    private Security security = new Security();
    private Schedule schedule = new Schedule();

    @Data
    public static class Quota {
        /** 新用户默认配额，500MB */
        private long defaultBytes = 524288000L;
    }

    @Data
    public static class Upload {
        /** 单文件上限 50MB */
        private long maxFileBytes = 52428800L;
        /** 上传凭证有效期 10 分钟 */
        private int presignExpireSeconds = 600;
        /** 下载链接有效期 5 分钟 */
        private int downloadExpireSeconds = 300;
        /** PENDING 记录存活时间 30 分钟 */
        private int pendingTimeoutMinutes = 30;
    }

    @Data
    public static class Avatar {
        /** 头像上限 5MB */
        private long maxBytes = 5242880L;
    }

    @Data
    public static class Trash {
        /** 回收站保留 14 天 */
        private int retentionDays = 14;
    }

    @Data
    public static class Share {
        private int defaultExpireDays = 7;
        private int codeLength = 4;
        private int codeMaxAttempts = 10;
        private int codeLockMinutes = 60;
    }

    @Data
    public static class Security {
        private int loginMaxFailures = 5;
        private int loginLockMinutes = 15;
        private int loginIpPerMinute = 10;
    }

    @Data
    public static class Schedule {
        private String pendingCleanupCron = "0 * * * * *";
        private String trashCleanupCron = "0 */10 * * * *";
        private String reconcileCron = "0 30 3 * * *";
    }
}
