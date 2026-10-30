package com.wherelee.etry.file;

import com.wherelee.etry.common.BusinessException;
import com.wherelee.etry.common.ErrorCode;
import com.wherelee.etry.storage.StorageGateway;
import com.wherelee.etry.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 文件状态变更的事务边界。
 *
 * 单独拆出来的原因：上传确认失败时既要回滚预占、又要删对象删记录，
 * 如果把这些清理和“抛异常”放在同一个事务里，异常会把清理一起回滚掉，
 * 留下孤立的 PENDING 记录和永远占着的预配额。所以清理必须跑在独立事务里。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileTransactionalOps {

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final StorageGateway storageGateway;

    /** 抢占 PENDING → READY 并结算预占；已被别的请求处理过时直接返回当前记录 */
    @Transactional
    public FileObject markReady(Long userId, Long fileId) {
        FileObject file = fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));

        int changed = fileRepository.transitionStatus(fileId, FileStatus.PENDING, FileStatus.READY);
        if (changed == 0) {
            return fileRepository.findById(fileId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        }

        userRepository.settleQuota(userId, file.getSizeBytes());
        file.setStatus(FileStatus.READY);
        file.setCommittedAt(LocalDateTime.now());
        return file;
    }

    /** 丢弃一次未完成的上传：删对象、退预占、删记录，全部在独立事务里完成 */
    @Transactional
    public void discard(Long fileId) {
        FileObject file = fileRepository.findById(fileId).orElse(null);
        if (file == null) {
            return;
        }
        storageGateway.delete(file.getObjectKey());
        userRepository.releaseReserved(file.getUserId(), file.getSizeBytes());
        fileRepository.delete(file);
        log.info("丢弃未完成上传 id={} key={}", file.getId(), file.getObjectKey());
    }
}
