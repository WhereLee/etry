package com.wherelee.etry.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "etry.storage")
public class StorageProperties {

    /** local 或 cos，决定注入哪个 StorageGateway 实现 */
    private String type = "local";

    private Local local = new Local();

    private Cos cos = new Cos();

    @Data
    public static class Local {
        private String root = "./data/storage";
        private String baseUrl = "http://127.0.0.1:8080/local-storage";
    }

    @Data
    public static class Cos {
        private String bucket;
        private String region;
        private String secretId;
        private String secretKey;
    }
}
