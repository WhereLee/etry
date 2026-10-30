package com.wherelee.etry.file;

import com.wherelee.etry.common.BusinessException;
import com.wherelee.etry.common.ErrorCode;
import com.wherelee.etry.config.EtryProperties;
import com.wherelee.etry.file.dto.FileDtos;
import com.wherelee.etry.share.ShareRepository;
import com.wherelee.etry.storage.ObjectStat;
import com.wherelee.etry.storage.PresignTicket;
import com.wherelee.etry.storage.StorageGateway;
import com.wherelee.etry.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final ShareRepository shareRepository;
    private final StorageGateway storageGateway;
    private final FileTransactionalOps ops;
    private final EtryProperties properties;

    /**
     * 签发直传凭证。顺序很关键：先在数据库里原子预占配额，
     * 挤不进去就直接拒，避免并发上传把配额顶穿。
     */
    @Transactional
    public FileDtos.PresignResponse presign(Long userId, String name, long sizeBytes) {
        String safeName = sanitizeName(name);
        long maxFileBytes = properties.getUpload().getMaxFileBytes();
        if (sizeBytes <= 0 || sizeBytes > maxFileBytes) {
            throw new BusinessException(ErrorCode.FILE_SIZE_INVALID, "单文件不能超过 50MB");
        }

        if (userRepository.reserveQuota(userId, sizeBytes) == 0) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED);
        }

        String contentType = ContentTypes.resolve(safeName);
        String objectKey = ObjectKeys.file(userId, safeName);
        // 只允许传声明的大小，凭证明细里的上限写死，超了 COS 直接拒收
        PresignTicket ticket = storageGateway.presignUpload(objectKey, sizeBytes, contentType,
                Duration.ofSeconds(properties.getUpload().getPresignExpireSeconds()));

        FileObject file = new FileObject();
        file.setUserId(userId);
        file.setObjectKey(objectKey);
        file.setOriginalName(safeName);
        file.setSizeBytes(sizeBytes);
        file.setContentType(contentType);
        file.setStatus(FileStatus.PENDING);
        fileRepository.save(file);

        return new FileDtos.PresignResponse(file.getId(), objectKey, contentType,
                ticket.url(), ticket.method(), ticket.formFields(), ticket.headers(), ticket.expireAt());
    }

    /**
     * 确认上传。幂等：重复调用不会重复扣配额。
     * 核对对象这一步故意放在事务外，失败时用独立事务清理，
     * 否则“抛异常”会把清理动作一起回滚掉。
     */
    public FileDtos.FileView commit(Long userId, Long fileId) {
        FileObject file = fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));

        if (file.getStatus() == FileStatus.READY) {
            return toView(file);
        }
        if (file.getStatus() != FileStatus.PENDING) {
            throw new BusinessException(ErrorCode.FILE_STATE_INVALID);
        }

        Optional<ObjectStat> stat = storageGateway.stat(file.getObjectKey());
        if (stat.isEmpty()) {
            ops.discard(fileId);
            throw new BusinessException(ErrorCode.OBJECT_NOT_FOUND);
        }
        if (stat.get().sizeBytes() != file.getSizeBytes()) {
            ops.discard(fileId);
            throw new BusinessException(ErrorCode.OBJECT_SIZE_MISMATCH);
        }
        return toView(ops.markReady(userId, fileId));
    }

    public void cancelPending(Long userId, Long fileId) {
        FileObject file = fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        if (file.getStatus() != FileStatus.PENDING) {
            throw new BusinessException(ErrorCode.FILE_STATE_INVALID);
        }
        ops.discard(fileId);
    }

    public FileDtos.PageView<FileDtos.FileView> list(Long userId, int page, int size, String sort, String keyword) {
        Sort sortSpec = "size".equalsIgnoreCase(sort)
                ? Sort.by(Sort.Direction.DESC, "sizeBytes")
                : Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sortSpec);

        Page<FileObject> result = (keyword == null || keyword.isBlank())
                ? fileRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, FileStatus.READY, pageable)
                : fileRepository.findByUserIdAndStatusAndDeletedAtIsNullAndOriginalNameContaining(
                        userId, FileStatus.READY, keyword.trim(), pageable);

        return new FileDtos.PageView<>(result.getContent().stream().map(this::toView).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    public List<FileDtos.PendingFileView> listPending(Long userId) {
        int timeoutMinutes = properties.getUpload().getPendingTimeoutMinutes();
        return fileRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, FileStatus.PENDING).stream()
                .map(file -> new FileDtos.PendingFileView(
                        file.getId(),
                        file.getOriginalName(),
                        file.getSizeBytes(),
                        file.getCreatedAt(),
                        file.getCreatedAt().plusMinutes(timeoutMinutes)))
                .toList();
    }

    public String downloadUrl(Long userId, Long fileId) {
        FileObject file = requireReady(userId, fileId);
        return storageGateway.presignDownload(file.getObjectKey(), file.getOriginalName(), file.getContentType(),
                Duration.ofSeconds(properties.getUpload().getDownloadExpireSeconds()));
    }

    @Transactional
    public FileDtos.FileView rename(Long userId, Long fileId, String newName) {
        FileObject file = requireReady(userId, fileId);
        file.setOriginalName(sanitizeName(newName));
        return toView(fileRepository.save(file));
    }

    /** 删除只进回收站，同时立刻作废这个文件的所有分享 */
    @Transactional
    public void moveToTrash(Long userId, Long fileId) {
        FileObject file = requireReady(userId, fileId);
        LocalDateTime now = LocalDateTime.now();
        file.setDeletedAt(now);
        file.setTrashExpiresAt(now.plusDays(properties.getTrash().getRetentionDays()));
        fileRepository.save(file);
        shareRepository.revokeByFileId(fileId, now);
    }

    FileObject requireReady(Long userId, Long fileId) {
        FileObject file = fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        if (file.getStatus() != FileStatus.READY || file.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.FILE_STATE_INVALID);
        }
        return file;
    }

    FileDtos.FileView toView(FileObject file) {
        return new FileDtos.FileView(file.getId(), file.getOriginalName(), file.getSizeBytes(),
                file.getContentType(), file.getCreatedAt());
    }

    static String sanitizeName(String raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "缺少文件名");
        }
        String cleaned = raw.replaceAll("\\p{Cntrl}", "").trim();
        if (cleaned.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "文件名不能为空");
        }
        return cleaned.length() > 255 ? cleaned.substring(0, 255) : cleaned;
    }
}
