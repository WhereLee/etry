package com.wherelee.edu.course;

import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.course.dto.CourseDtos;
import com.wherelee.edu.security.CurrentUser;
import com.wherelee.edu.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台课程。需要登录（不在白名单里）。
 *
 * 目前没有购买功能，所以已上架课程对登录用户全部可见。
 * 等购买做完，把这里和 play() 的准入条件收成「已购」即可，接口形态不用变。
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CoursePublicController {

    private final CoursePublicService coursePublicService;

    @GetMapping
    public ApiResponse<PageResult<CourseDtos.CourseSummaryVO>> page(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(coursePublicService.page(subjectId, tagId, level, keyword, page, size));
    }

    /**
     * 详情。匿名也能访问，所以不用 @CurrentUser（它取不到人会抛异常）；
     * 直接从 UserContext 拿，拿不到就当匿名。
     */
    @GetMapping("/{id}")
    public ApiResponse<CourseDtos.CourseDetailVO> detail(@PathVariable Long id) {
        UserContext.Principal principal = UserContext.get();
        Long userId = principal == null ? null : principal.userId();
        return ApiResponse.ok(coursePublicService.detail(id, userId));
    }

    /** 播放地址：预签名 GET，默认 2 小时有效。顺序课会先校验上一节是否学完 */
    @GetMapping("/lessons/{id}/play")
    public ApiResponse<CourseDtos.PlayVO> play(@CurrentUser Long userId, @PathVariable Long id) {
        return ApiResponse.ok(coursePublicService.play(userId, id));
    }
}
