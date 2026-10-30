package com.wherelee.edu.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.Date;
import java.util.UUID;

/**
 * 对象存储实现：用户上传的文件落在 COS，服务器磁盘上不留任何用户文件。
 *
 * 桶是私有读，所以 url() 返回的是预签名 URL（有效期可配，默认 2 小时）。
 * 页面刷新时前端重新调 profile 接口就会拿到新链接，过期不影响使用。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "cos", matchIfMissing = true)
public class CosFileStorage implements FileStorage {

    private final COSClient client;
    private final String bucket;
    private final String keyPrefix;
    private final int urlTtlMinutes;

    public CosFileStorage(StorageProperties properties) {
        StorageProperties.Cos cos = properties.cos();
        if (cos == null || !cos.configured()) {
            throw new IllegalStateException(
                    "未配置对象存储凭据：请设置环境变量 COS_SECRET_ID / COS_SECRET_KEY，"
                            + "或在本地调试时设置 STORAGE_TYPE=local 使用本机磁盘");
        }
        COSCredentials credentials = new BasicCOSCredentials(cos.secretId(), cos.secretKey());
        this.client = new COSClient(credentials, new ClientConfig(new Region(cos.region())));
        this.bucket = cos.bucket();
        this.keyPrefix = normalizePrefix(cos.keyPrefix());
        this.urlTtlMinutes = cos.urlTtlMinutes();
        log.info("对象存储已启用 bucket={} prefix={} urlTtl={}min", bucket, keyPrefix, urlTtlMinutes);
    }

    @Override
    public String store(byte[] content, String extension, String directory) {
        String key = keyPrefix + directory + "/" + UUID.randomUUID().toString().replace("-", "") + "." + extension;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        metadata.setContentType(contentTypeOf(extension));

        client.putObject(new PutObjectRequest(bucket, key, new ByteArrayInputStream(content), metadata));
        log.info("文件已上传到对象存储 key={} size={}", key, content.length);
        return key;
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            client.deleteObject(bucket, key);
        } catch (Exception e) {
            // 删除失败不能影响主流程，留个日志便于人工清理
            log.warn("删除对象失败 key={}", key, e);
        }
    }

    @Override
    public String url(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        GeneratePresignedUrlRequest request =
                new GeneratePresignedUrlRequest(bucket, key, HttpMethodName.GET);
        request.setExpiration(new Date(System.currentTimeMillis() + urlTtlMinutes * 60_000L));
        URL url = client.generatePresignedUrl(request);
        return url.toString();
    }

    @PreDestroy
    public void shutdown() {
        client.shutdown();
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private String contentTypeOf(String extension) {
        return switch (extension.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }
}
