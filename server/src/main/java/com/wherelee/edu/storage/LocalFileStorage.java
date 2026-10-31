package com.wherelee.edu.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

/**
 * 本地磁盘存储：仅在 app.storage.type=local 时启用，用于没配 COS 凭据的本地调试。
 * 生产环境一律走 {@link CosFileStorage}，服务器磁盘不落用户文件。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local")
public class LocalFileStorage implements FileStorage {

    private static final String URL_PREFIX = "/files/";

    private final Path root;

    public LocalFileStorage(StorageProperties properties) {
        this.root = Paths.get(properties.localDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("无法创建上传目录: " + root, e);
        }
        log.info("本地文件存储根目录 {}", root);
    }

    @Override
    public String store(byte[] content, String extension, String directory) {
        String key = directory + "/" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            // 防目录穿越：key 由本方法生成，这里是兜底
            throw new IllegalArgumentException("非法的存储路径: " + key);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("写入文件失败: " + key, e);
        }
        return key;
    }

    @Override
    public String newKey(String directory, String extension) {
        return directory + "/" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
    }

    /**
     * 本地存储没法直传：前端拿不到可直接 PUT 的地址。
     * 想测视频上传必须配 COS 凭据（STORAGE_TYPE=cos）。
     */
    @Override
    public String presignedPutUrl(String key, String contentType, Duration ttl) {
        throw new UnsupportedOperationException(
                "本地存储不支持直传，请配置 COS 凭据并使用 STORAGE_TYPE=cos");
    }

    @Override
    public boolean ownsKey(String key, String directory) {
        if (key == null || key.isBlank() || directory == null || directory.isBlank()) {
            return false;
        }
        return key.startsWith(directory + "/");
    }

    @Override
    public byte[] readRange(String key, long start, long length) {
        Path target = resolveSafe(key);
        int capacity = (int) Math.min(length, Integer.MAX_VALUE);
        try (RandomAccessFile raf = new RandomAccessFile(target.toFile(), "r")) {
            raf.seek(start);
            byte[] buffer = new byte[capacity];
            int read = raf.read(buffer);
            return read <= 0 ? new byte[0] : Arrays.copyOf(buffer, read);
        } catch (IOException e) {
            throw new UncheckedIOException("读取文件区间失败: " + key, e);
        }
    }

    @Override
    public long size(String key) {
        try {
            return Files.size(resolveSafe(key));
        } catch (IOException e) {
            throw new UncheckedIOException("读取文件大小失败: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(key).normalize());
        } catch (IOException e) {
            log.warn("删除文件失败 key={}", key, e);
        }
    }

    @Override
    public String url(String key) {
        return key == null || key.isBlank() ? null : URL_PREFIX + key;
    }

    /** 防目录穿越：key 虽然由本服务生成，但读到外部 key 时也要兼顾 */
    private Path resolveSafe(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法的存储路径: " + key);
        }
        return target;
    }
}
