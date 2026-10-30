package com.wherelee.edu.notice;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.notice.dto.NoticeDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

    private static final int MAX_PAGE_SIZE = 50;

    private final NoticeMapper noticeMapper;

    /** 前台：只看已发布 */
    public PageResult<NoticeDtos.NoticeSummaryVO> publicPage(int page, int size) {
        return page(Notice.STATUS_PUBLISHED, page, size);
    }

    public PageResult<NoticeDtos.NoticeSummaryVO> adminPage(String status, int page, int size) {
        return page(status, page, size);
    }

    public NoticeDtos.NoticeVO detail(Long id) {
        Notice notice = noticeMapper.findById(id);
        if (notice == null) {
            throw BizException.of(ErrorCode.NOTICE_NOT_FOUND);
        }
        return NoticeDtos.NoticeVO.from(notice);
    }

    /** 前台详情只允许看已发布的 */
    public NoticeDtos.NoticeVO publicDetail(Long id) {
        NoticeDtos.NoticeVO vo = detail(id);
        if (!Notice.STATUS_PUBLISHED.equals(vo.status())) {
            throw BizException.of(ErrorCode.NOTICE_NOT_FOUND);
        }
        return vo;
    }

    @Transactional
    public NoticeDtos.NoticeVO create(Long publisherId, NoticeDtos.SaveRequest request) {
        Notice notice = new Notice();
        notice.setTitle(request.title());
        notice.setContent(request.content());
        notice.setPublisherId(publisherId);
        notice.setIsTop(Boolean.TRUE.equals(request.isTop()));
        String status = request.status() == null ? Notice.STATUS_DRAFT : request.status();
        notice.setStatus(status);
        notice.setPublishedAt(Notice.STATUS_PUBLISHED.equals(status) ? LocalDateTime.now() : null);
        noticeMapper.insert(notice);
        log.info("公告创建 id={} status={} publisher={}", notice.getId(), status, publisherId);
        return NoticeDtos.NoticeVO.from(notice);
    }

    @Transactional
    public void update(Long id, NoticeDtos.SaveRequest request) {
        Notice notice = noticeMapper.findById(id);
        if (notice == null) {
            throw BizException.of(ErrorCode.NOTICE_NOT_FOUND);
        }
        notice.setTitle(request.title());
        notice.setContent(request.content());
        notice.setIsTop(Boolean.TRUE.equals(request.isTop()));
        String status = request.status() == null ? notice.getStatus() : request.status();
        // 首次发布时才写发布时间；下架再上架不覆盖原始发布时间
        if (Notice.STATUS_PUBLISHED.equals(status) && notice.getPublishedAt() == null) {
            notice.setPublishedAt(LocalDateTime.now());
        }
        notice.setStatus(status);
        noticeMapper.update(notice);
        log.info("公告更新 id={} status={}", id, status);
    }

    @Transactional
    public void delete(Long id) {
        if (noticeMapper.findById(id) == null) {
            throw BizException.of(ErrorCode.NOTICE_NOT_FOUND);
        }
        noticeMapper.deleteById(id);
        log.info("公告删除 id={}", id);
    }

    private PageResult<NoticeDtos.NoticeSummaryVO> page(String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = noticeMapper.countPage(status);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<NoticeDtos.NoticeSummaryVO> records = noticeMapper
                .findPage(status, (safePage - 1) * safeSize, safeSize)
                .stream().map(NoticeDtos.NoticeSummaryVO::from).toList();
        return PageResult.of(records, total, safePage, safeSize);
    }
}
