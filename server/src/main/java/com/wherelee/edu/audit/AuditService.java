package com.wherelee.edu.audit;

import com.wherelee.edu.common.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DETAIL_MAX_LENGTH = 500;

    private final AuditMapper auditMapper;

    /**
     * 审计写入用独立事务：业务方法回滚了，审计记录仍应保留（失败也要能查到）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditRecord record) {
        if (record.getDetail() != null && record.getDetail().length() > DETAIL_MAX_LENGTH) {
            record.setDetail(record.getDetail().substring(0, DETAIL_MAX_LENGTH));
        }
        auditMapper.insert(record);
    }

    public PageResult<AuditRecord> page(Long userId, String action, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = auditMapper.countPage(userId, action);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<AuditRecord> records = auditMapper.findPage(userId, action, (safePage - 1) * safeSize, safeSize);
        return PageResult.of(records, total, safePage, safeSize);
    }
}
