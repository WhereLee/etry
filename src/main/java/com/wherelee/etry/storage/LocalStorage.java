package com.wherelee.etry.storage;

import com.wherelee.etry.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 本地磁盘实现，只用于开发和测试，让整条链路在没有 COS 凭据时也能跑通。
 * 生产环境不会装配这个类。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "etry.storage.type", havingValue = "local")
public class LocalStorage implements StorageGateway {

    private final StorageProperties properties;

    @Override
    public PresignTicket presignUpload(String objectKey, long sizeBytes, String contentType, Duration ttl) {
        String url = baseUrl()
                + "/upload?key=" + URLEncoder.encode(objectKey, StandardCharsets.UTF_8);
        return PresignTicket.postForm(url, Map.of(), Instant.now().plus(ttl));
    }

    @Override
    public String presignDownload(String objectKey, String downloadName, String contentType, Duration ttl) {
        return baseUrl()
                + "/download?key=" + URLEncoder.encode(objectKey, StandardCharsets.UTF_8)
                + "&name=" + URLEncoder.encode(downloadName, StandardCharsets.UTF_8);
    }

    /** 默认为相对路径，浏览器与任意端口的实例都能直接用 */
    private String baseUrl() {
        String base = properties.getLocal().getBaseUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    @Override
    public Optional<ObjectStat> stat(String objectKey) {
        Path path = resolve(objectKey);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ObjectStat(Files.size(path), Files.probeContentType(path)));
        } catch (IOException e) {
            log.warn("读取本地对象失败 key={}", objectKey, e);
            return Optional.empty();
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException e) {
            log.warn("删除本地对象失败 key={}", objectKey, e);
        }
    }

    Path resolve(String objectKey) {
        Path root = Paths.get(properties.getLocal().getRoot()).toAbsolutePath().normalize();
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法的对象 key: " + objectKey);
        }
        return target;
    }
}
