package com.wherelee.edu.audit;

import com.wherelee.edu.common.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    /**
     * 审计专用 logger，对应 logback 里 name="AUDIT" 的 appender，单独写 audit.log。
     * 不能用 @Slf4j 的 log——那个走 root，会落到 app.log 里。
     */
    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    private static final int MAX_PAGE_SIZE = 50;
    /** 页码硬上限，防止 (page-1)*size 溢出成负 offset */
    private static final int MAX_PAGE = 10_000;
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
        AUDIT.info("action={} userId={} username={} target={}:{} success={} costMs={} ip={} detail={}",
                record.getAction(), record.getUserId(), record.getUsername(),
                record.getTargetType(), record.getTargetId(),
                record.getSuccess(), record.getCostMs(), record.getIp(), record.getDetail());
    }

    public PageResult<AuditRecord> page(Long userId, String action, int page, int size) {
        int safePage = Math.min(Math.max(page, 1), MAX_PAGE);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = auditMapper.countPage(userId, action);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<AuditRecord> records = auditMapper.findPage(userId, action, (safePage - 1) * safeSize, safeSize);
        return PageResult.of(records, total, safePage, safeSize);
    }
}
