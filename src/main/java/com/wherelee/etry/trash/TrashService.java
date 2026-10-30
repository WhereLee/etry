package com.wherelee.etry.trash;

import com.wherelee.etry.common.BusinessException;
import com.wherelee.etry.common.ErrorCode;
import com.wherelee.etry.config.EtryProperties;
import com.wherelee.etry.file.FileObject;
import com.wherelee.etry.file.FileRepository;
import com.wherelee.etry.file.FileStatus;
import com.wherelee.etry.share.ShareRepository;
import com.wherelee.etry.storage.StorageGateway;
import com.wherelee.etry.user.User;
import com.wherelee.etry.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 回收站。14 天内可恢复，期间仍然占用配额，彻底删除才把空间放出来。
 */
@Service
@RequiredArgsConstructor
public class TrashService {

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final ShareRepository shareRepository;
    private final StorageGateway storageGateway;
    private final EtryProperties properties;

    public PageView list(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "deletedAt"));
        Page<FileObject> result = fileRepository.findByUserIdAndDeletedAtIsNotNull(userId, pageable);
        LocalDateTime now = LocalDateTime.now();
        List<TrashItem> items = result.getContent().stream()
                .map(file -> new TrashItem(
                        file.getId(),
                        file.getOriginalName(),
                        file.getSizeBytes(),
                        file.getDeletedAt(),
                        file.getTrashExpiresAt(),
                        remainingDays(now, file.getTrashExpiresAt())))
                .toList();
        return new PageView(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional
    public void restore(Long userId, Long fileId) {
        FileObject file = requireTrashed(userId, fileId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getUsedBytes() + user.getReservedBytes() > user.getQuotaBytes()) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED);
        }
        file.setDeletedAt(null);
        file.setTrashExpiresAt(null);
        fileRepository.save(file);
    }

    /**
     * 彻底删除：先抢占状态防止与清理任务撞车，再删对象、删分享、删记录、退配额。
     * 恢复出来的分享不会复活，因为删除时已经作废了。
     */
    @Transactional
    public void purge(Long userId, Long fileId) {
        FileObject file = requireTrashed(userId, fileId);
        int changed = fileRepository.transitionStatus(fileId, FileStatus.READY, FileStatus.PURGING);
        if (changed == 0) {
            throw new BusinessException(ErrorCode.FILE_STATE_INVALID);
        }
        storageGateway.delete(file.getObjectKey());
        shareRepository.deleteByFileId(fileId);
        fileRepository.deleteById(fileId);
        userRepository.releaseUsed(userId, file.getSizeBytes());
    }

    FileObject requireTrashed(Long userId, Long fileId) {
        FileObject file = fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        if (file.getDeletedAt() == null || file.getStatus() != FileStatus.READY) {
            throw new BusinessException(ErrorCode.FILE_STATE_INVALID);
        }
        return file;
    }

    private long remainingDays(LocalDateTime now, LocalDateTime expireAt) {
        if (expireAt == null) {
            return 0;
        }
        long days = Duration.between(now, expireAt).toDays();
        return Math.max(days, 0);
    }

    public record TrashItem(Long id, String name, long sizeBytes, LocalDateTime deletedAt,
                            LocalDateTime expiresAt, long remainingDays) {
    }

    public record PageView(List<TrashItem> items, int page, int size, long total) {
    }
}
