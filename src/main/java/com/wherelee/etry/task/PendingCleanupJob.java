package com.wherelee.etry.task;

import com.wherelee.etry.config.EtryProperties;
import com.wherelee.etry.file.FileObject;
import com.wherelee.etry.file.FileRepository;
import com.wherelee.etry.file.FileStatus;
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
 * 清理超时未确认的上传：30 分钟还没 commit 的记录，
 * 先抢占状态再删对象、退预占、删记录，避免孤儿对象和脏记录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingCleanupJob {

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final StorageGateway storageGateway;
    private final EtryProperties properties;

    @Scheduled(cron = "${etry.schedule.pending-cleanup-cron}")
    @Transactional
    public void cleanup() {
        LocalDateTime deadline = LocalDateTime.now()
                .minusMinutes(properties.getUpload().getPendingTimeoutMinutes());
        List<FileObject> expired = fileRepository.findByStatusAndCreatedAtBefore(FileStatus.PENDING, deadline);
        if (expired.isEmpty()) {
            return;
        }
        for (FileObject file : expired) {
            int changed = fileRepository.transitionStatus(file.getId(), FileStatus.PENDING, FileStatus.PURGING);
            if (changed == 0) {
                continue;
            }
            storageGateway.delete(file.getObjectKey());
            userRepository.releaseReserved(file.getUserId(), file.getSizeBytes());
            fileRepository.deleteById(file.getId());
            log.info("清理超时未完成上传 id={} key={}", file.getId(), file.getObjectKey());
        }
    }
}
