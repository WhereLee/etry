package com.wherelee.edu.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String type, String localDir, Cos cos) {

    /**
     * 对象存储配置。密钥走环境变量，不进配置文件。
     */
    public record Cos(
            String bucket,
            String region,
            String secretId,
            String secretKey,
            String keyPrefix,
            int urlTtlMinutes
    ) {

        public boolean configured() {
            return secretId != null && !secretId.isBlank()
                    && secretKey != null && !secretKey.isBlank();
        }
    }
}
