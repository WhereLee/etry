package com.wherelee.edu.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CosPlaybackService {

    private final COSClient client;
    private final String bucket;
    private final int urlTtlMinutes;

    public CosPlaybackService(CosProperties properties) {
        Map<String, String> local = readLocalCosConf();
        String secretId = firstNonBlank(System.getenv("COS_SECRET_ID"), local.get("secret_id"));
        String secretKey = firstNonBlank(System.getenv("COS_SECRET_KEY"), local.get("secret_key"));
        this.bucket = firstNonBlank(properties.bucket(), local.get("bucket"));
        String region = firstNonBlank(properties.region(), local.get("region"));
        this.urlTtlMinutes = properties.urlTtlMinutes();

        if (secretId == null || secretKey == null) {
            throw new IllegalStateException("缺少 COS 凭据：请设置 COS_SECRET_ID / COS_SECRET_KEY，或配置 ~/.cos.conf");
        }
        if (bucket == null || region == null) {
            throw new IllegalStateException("缺少 COS bucket 或 region 配置");
        }
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        clientConfig.setConnectionTimeout(properties.connectTimeoutMs());
        clientConfig.setSocketTimeout(properties.readTimeoutMs());
        clientConfig.setConnectionRequestTimeout(properties.connectTimeoutMs());
        clientConfig.setMaxErrorRetry(properties.maxRetries());
        this.client = new COSClient(new BasicCOSCredentials(secretId, secretKey), clientConfig);
        log.info("COS 播放已启用 bucket={} region={} urlTtl={}min connectTimeout={}ms readTimeout={}ms retries={}",
                bucket, region, urlTtlMinutes, properties.connectTimeoutMs(), properties.readTimeoutMs(), properties.maxRetries());
    }

    public String presignedGetUrl(String key) {
        return presignedGetUrl(key, Duration.ofMinutes(urlTtlMinutes));
    }

    public String presignedGetUrl(String key, Duration ttl) {
        GeneratePresignedUrlRequest request =
                new GeneratePresignedUrlRequest(bucket, key, HttpMethodName.GET);
        request.setExpiration(new Date(System.currentTimeMillis() + ttl.toMillis()));
        return client.generatePresignedUrl(request).toString();
    }

    public String presignedPutUrl(String key, String contentType, Duration ttl) {
        GeneratePresignedUrlRequest request =
                new GeneratePresignedUrlRequest(bucket, key, HttpMethodName.PUT);
        request.setContentType(contentType);
        request.setExpiration(new Date(System.currentTimeMillis() + ttl.toMillis()));
        return client.generatePresignedUrl(request).toString();
    }

    public ObjectMetadata objectMetadata(String key) {
        return client.getObjectMetadata(bucket, key);
    }

    public void deleteObject(String key) {
        client.deleteObject(bucket, key);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private static Map<String, String> readLocalCosConf() {
        Path path = Paths.get(System.getProperty("user.home"), ".cos.conf");
        Map<String, String> values = new HashMap<>();
        if (!Files.exists(path)) {
            return values;
        }
        try {
            boolean common = false;
            List<String> lines = Files.readAllLines(path);
            for (String raw : lines) {
                String line = raw.trim();
                if (line.startsWith("[") && line.endsWith("]")) {
                    common = "[common]".equals(line);
                    continue;
                }
                if (!common || line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                int index = line.indexOf('=');
                values.put(line.substring(0, index).trim(), line.substring(index + 1).trim());
            }
        } catch (IOException e) {
            log.warn("读取 ~/.cos.conf 失败，将只使用环境变量", e);
        }
        return values;
    }

    @PreDestroy
    public void shutdown() {
        client.shutdown();
    }
}
