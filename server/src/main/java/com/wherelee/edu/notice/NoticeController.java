package com.wherelee.edu.notice;

import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.notice.dto.NoticeDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台公告：只看已发布，不需要任何权限。
 */
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public ApiResponse<PageResult<NoticeDtos.NoticeSummaryVO>> page(@RequestParam(defaultValue = "1") int page,
                                                                    @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(noticeService.publicPage(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<NoticeDtos.NoticeVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(noticeService.publicDetail(id));
    }
}
