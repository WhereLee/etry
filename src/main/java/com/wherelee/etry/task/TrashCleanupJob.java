package com.wherelee.etry.task;

import com.wherelee.etry.file.FileObject;
import com.wherelee.etry.file.FileRepository;
import com.wherelee.etry.file.FileStatus;
import com.wherelee.etry.share.ShareRepository;
import com.wherelee.etry.storage.StorageGateway;
import com.wherelee.etry.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回收站到期清理：14 天到了就物理删除，同时删对象、删分享、释放配额。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrashCleanupJob {

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final ShareRepository shareRepository;
    private final StorageGateway storageGateway;

    @Scheduled(cron = "${etry.schedule.trash-cleanup-cron}")
    @Transactional
    public void cleanup() {
        List<FileObject> expired =
                fileRepository.findByDeletedAtIsNotNullAndTrashExpiresAtBefore(LocalDateTime.now());
        if (expired.isEmpty()) {
            return;
        }
        for (FileObject file : expired) {
            int changed = fileRepository.transitionStatus(file.getId(), FileStatus.READY, FileStatus.PURGING);
            if (changed == 0) {
                continue;
            }
            storageGateway.delete(file.getObjectKey());
            shareRepository.deleteByFileId(file.getId());
            fileRepository.deleteById(file.getId());
            userRepository.releaseUsed(file.getUserId(), file.getSizeBytes());
            log.info("回收站到期清理 id={} key={}", file.getId(), file.getObjectKey());
        }
    }
}
