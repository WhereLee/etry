package com.wherelee.etry.share;

import com.wherelee.etry.cache.KeyValueStore;
import com.wherelee.etry.common.BusinessException;
import com.wherelee.etry.common.ErrorCode;
import com.wherelee.etry.config.EtryProperties;
import com.wherelee.etry.file.FileObject;
import com.wherelee.etry.file.FileRepository;
import com.wherelee.etry.file.FileStatus;
import com.wherelee.etry.storage.StorageGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * 分享：生成一个短 token，访客拿到后 302 跳转到预签名下载地址。
 * 文件字节仍然不经过服务器，分享不吃带宽。
 */
@Service
@RequiredArgsConstructor
public class ShareService {

    private final ShareRepository shareRepository;
    private final FileRepository fileRepository;
    private final StorageGateway storageGateway;
    private final PasswordEncoder passwordEncoder;
    private final EtryProperties properties;
    private final KeyValueStore keyValueStore;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final String SHARE_FAIL_KEY = "etry:share:fail:";

    @Transactional
    public ShareView create(Long userId, Long fileId, Integer expireDays, String code) {
        FileObject file = fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        if (file.getStatus() != FileStatus.READY || file.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.FILE_STATE_INVALID);
        }

        int days = properties.getShare().getDefaultExpireDays();
        if (expireDays != null && expireDays > 0 && expireDays <= days) {
            days = expireDays;
        }

        String hashedCode = null;
        if (code != null && !code.isBlank()) {
            if (!code.matches("\\d{" + properties.getShare().getCodeLength() + "}")) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "提取码为 4 位数字");
            }
            hashedCode = passwordEncoder.encode(code);
        }

        Share share = new Share();
        share.setFileId(fileId);
        share.setUserId(userId);
        share.setToken(randomToken());
        share.setCode(hashedCode);
        share.setExpiresAt(LocalDateTime.now().plusDays(days));
        shareRepository.save(share);

        return toView(share, file.getOriginalName());
    }

    public List<ShareView> list(Long userId) {
        return shareRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(share -> {
                    String name = fileRepository.findById(share.getFileId())
                            .map(FileObject::getOriginalName)
                            .orElse("(已删除)");
                    return toView(share, name);
                })
                .toList();
    }

    @Transactional
    public void revoke(Long userId, Long shareId) {
        Share share = shareRepository.findByIdAndUserId(shareId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_NOT_FOUND));
        if (share.getRevokedAt() == null) {
            share.setRevokedAt(LocalDateTime.now());
            shareRepository.save(share);
        }
    }

    /**
     * 打开分享页时需要知道：文件叫什么、要不要提取码、还活不活着。
     */
    public ShareAccess resolve(String token) {
        Share share = shareRepository.findByToken(token).orElse(null);
        if (share == null) {
            return ShareAccess.invalid("分享不存在");
        }
        if (share.getRevokedAt() != null) {
            return ShareAccess.invalid("分享已被取消");
        }
        if (share.getExpiresAt().isBefore(LocalDateTime.now())) {
            return ShareAccess.invalid("分享已过期");
        }
        FileObject file = fileRepository.findById(share.getFileId()).orElse(null);
        if (file == null || file.getStatus() != FileStatus.READY || file.getDeletedAt() != null) {
            return ShareAccess.invalid("分享已失效");
        }
        return new ShareAccess(true, null, share, file);
    }

    /**
     * 校验提取码并签发下载地址。失败次数按 token + IP 计，超过就锁一段时间。
     */
    public String unlock(String token, String code, String ip) {
        ShareAccess access = resolve(token);
        if (!access.valid()) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND, access.message());
        }
        Share share = access.share();
        if (share.getCode() != null) {
            String failKey = SHARE_FAIL_KEY + token + ":" + ip;
            String failValue = keyValueStore.get(failKey);
            long failures = failValue == null ? 0 : Long.parseLong(failValue);
            if (failures >= properties.getShare().getCodeMaxAttempts()) {
                throw new BusinessException(ErrorCode.SHARE_CODE_LOCKED);
            }
            if (code == null || code.isBlank()) {
                throw new BusinessException(ErrorCode.SHARE_CODE_REQUIRED);
            }
            if (!passwordEncoder.matches(code, share.getCode())) {
                long after = keyValueStore.incr(failKey);
                keyValueStore.expire(failKey, Duration.ofMinutes(properties.getShare().getCodeLockMinutes()));
                if (after >= properties.getShare().getCodeMaxAttempts()) {
                    throw new BusinessException(ErrorCode.SHARE_CODE_LOCKED);
                }
                throw new BusinessException(ErrorCode.SHARE_CODE_INVALID);
            }
            keyValueStore.delete(failKey);
        }
        return storageGateway.presignDownload(access.file().getObjectKey(), access.file().getOriginalName(),
                access.file().getContentType(),
                Duration.ofSeconds(properties.getUpload().getDownloadExpireSeconds()));
    }

    private String randomToken() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ShareView toView(Share share, String fileName) {
        long remaining = Math.max(Duration.between(LocalDateTime.now(), share.getExpiresAt()).toDays(), 0);
        return new ShareView(
                share.getId(),
                share.getToken(),
                "/s/" + share.getToken(),
                fileName,
                share.getExpiresAt(),
                remaining,
                share.getCode() != null,
                share.getRevokedAt() != null,
                share.getCreatedAt());
    }

    public record ShareView(Long id, String token, String shareUrl, String fileName,
                            LocalDateTime expiresAt, long remainingDays, boolean needCode,
                            boolean revoked, LocalDateTime createdAt) {
    }

    public record ShareAccess(boolean valid, String message, Share share, FileObject file) {
        static ShareAccess invalid(String message) {
            return new ShareAccess(false, message, null, null);
        }
    }
}
