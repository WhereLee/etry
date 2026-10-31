package com.wherelee.edu.learning;

import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.learning.dto.LearningDtos;
import com.wherelee.edu.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学习进度。登录即可用，不区分角色——学员能看课，老师自己也得能看别人的课学习。
 */
@RestController
@RequestMapping("/api/learning")
@RequiredArgsConstructor
public class LearningController {

    private final LearningService learningService;

    /**
     * 上报播放进度。播放器每 10 秒左右调一次，是这套接口里调用最频繁的一个。
     * 故不挂审计——审计是给管理动作留痕的，不是给播放心跳用的。
     */
    @PostMapping("/progress")
    public ApiResponse<Void> report(@CurrentUser Long userId,
                                    @Valid @RequestBody LearningDtos.ReportRequest request) {
        learningService.report(userId, request);
        return ApiResponse.ok();
    }

    /** 某门课的进度：每个课时播到第几秒、哪些学完了 */
    @GetMapping("/courses/{courseId}")
    public ApiResponse<LearningDtos.CourseProgressVO> courseProgress(@CurrentUser Long userId,
                                                                     @PathVariable Long courseId) {
        return ApiResponse.ok(learningService.courseProgress(userId, courseId));
    }

    /** 我的学习：按最近学习时间倒序 */
    @GetMapping("/my-courses")
    public ApiResponse<List<LearningDtos.MyCourseVO>> myCourses(@CurrentUser Long userId,
                                                                @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(learningService.myCourses(userId, limit));
    }
}
