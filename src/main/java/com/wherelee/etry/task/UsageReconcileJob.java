package com.wherelee.etry.task;

import com.wherelee.etry.file.FileObject;
import com.wherelee.etry.file.FileRepository;
import com.wherelee.etry.file.FileStatus;
import com.wherelee.etry.storage.StorageGateway;
import com.wherelee.etry.user.User;
import com.wherelee.etry.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 对账：按 files 表里 READY 的记录重算每个用户的已用字节，
 * 顺带核对对象是否还在。数字对不上以重算结果为准并留日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageReconcileJob {

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final StorageGateway storageGateway;

    @Scheduled(cron = "${etry.schedule.reconcile-cron}")
    @Transactional
    public void reconcile() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            long expected = 0;
            for (FileObject file : fileRepository.findByUserId(user.getId())) {
                if (file.getStatus() != FileStatus.READY) {
                    continue;
                }
                expected += file.getSizeBytes();
                if (storageGateway.stat(file.getObjectKey()).isEmpty()) {
                    log.warn("对账发现对象缺失 userId={} fileId={} key={}",
                            user.getId(), file.getId(), file.getObjectKey());
                }
            }
            if (expected != user.getUsedBytes()) {
                log.warn("对账修正已用配额 userId={} before={} after={}",
                        user.getId(), user.getUsedBytes(), expected);
                userRepository.updateUsedBytes(user.getId(), expected);
            }
        }
    }
}
