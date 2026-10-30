package com.wherelee.etry.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.ResponseHeaderOverrides;
import com.wherelee.etry.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

/**
 * 腾讯云 COS 实现。桶为私有读写，上传与下载都靠短时签名放行。
 *
 * 上传用 PUT 预签名 URL：由 SDK 生成，签名正确性有保证，浏览器直接把文件体 PUT 上去，
 * 字节不经过应用服务器。Content-Type 被签进 URL，客户端必须带同一个头。
 * 体积与类型约束由服务端在签发时校验、在 commit 时按对象真实大小再核一遍。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "etry.storage.type", havingValue = "cos")
public class CosStorage implements StorageGateway {

    private final COSClient cosClient;
    private final StorageProperties properties;

    @Override
    public PresignTicket presignUpload(String objectKey, long sizeBytes, String contentType, Duration ttl) {
        StorageProperties.Cos cos = properties.getCos();
        Instant expireAt = Instant.now().plus(ttl);

        GeneratePresignedUrlRequest request =
                new GeneratePresignedUrlRequest(cos.getBucket(), objectKey, HttpMethodName.PUT);
        request.setExpiration(Date.from(expireAt));
        request.putCustomRequestHeader("Content-Type", contentType);

        URL url = cosClient.generatePresignedUrl(request);
        return PresignTicket.put(url.toString(), Map.of("Content-Type", contentType), expireAt);
    }

    @Override
    public String presignDownload(String objectKey, String downloadName, String contentType, Duration ttl) {
        StorageProperties.Cos cos = properties.getCos();
        GeneratePresignedUrlRequest request =
                new GeneratePresignedUrlRequest(cos.getBucket(), objectKey, HttpMethodName.GET);
        request.setExpiration(Date.from(Instant.now().plus(ttl)));

        ResponseHeaderOverrides overrides = new ResponseHeaderOverrides();
        overrides.setContentDisposition(contentDisposition(downloadName));
        overrides.setContentType(contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType);
        request.setResponseHeaders(overrides);

        URL url = cosClient.generatePresignedUrl(request);
        return url.toString();
    }

    @Override
    public Optional<ObjectStat> stat(String objectKey) {
        StorageProperties.Cos cos = properties.getCos();
        if (!cosClient.doesObjectExist(cos.getBucket(), objectKey)) {
            return Optional.empty();
        }
        ObjectMetadata metadata = cosClient.getObjectMetadata(cos.getBucket(), objectKey);
        return Optional.of(new ObjectStat(metadata.getContentLength(), metadata.getContentType()));
    }

    @Override
    public void delete(String objectKey) {
        try {
            cosClient.deleteObject(properties.getCos().getBucket(), objectKey);
        } catch (Exception e) {
            log.warn("删除 COS 对象失败 key={}", objectKey, e);
        }
    }

    /** RFC 5987，兼容中文与空格 */
    static String contentDisposition(String downloadName) {
        String fallback = downloadName.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "_");
        String encoded = java.net.URLEncoder.encode(downloadName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    }
}
