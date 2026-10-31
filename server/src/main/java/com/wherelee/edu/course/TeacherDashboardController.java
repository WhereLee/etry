package com.wherelee.edu.course;

import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.course.dto.DashboardDtos;
import com.wherelee.edu.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 讲师工作台：我的课有多少人学、完课率、评价均分、待回复提问。
 * 需要 APPROVED 讲师档案，校验在 Service 里做。
 */
@RestController
@RequestMapping("/api/teacher/dashboard")
@RequiredArgsConstructor
public class TeacherDashboardController {

    private final TeacherDashboardService dashboardService;

    @GetMapping
    public ApiResponse<DashboardDtos.DashboardVO> dashboard(@CurrentUser Long userId) {
        return ApiResponse.ok(dashboardService.dashboard(userId));
    }
}
