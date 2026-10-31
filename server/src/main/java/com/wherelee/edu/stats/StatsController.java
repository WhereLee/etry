package com.wherelee.edu.stats;

import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.rbac.RequirePermission;
import com.wherelee.edu.stats.dto.StatsDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运营看板。
 *
 * 不挂审计：看数据是只读操作，记进审计只会把真正的管理动作淹掉。
 */
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    @RequirePermission("stats:view")
    public ApiResponse<StatsDtos.OverviewVO> overview() {
        return ApiResponse.ok(statsService.overview());
    }

    @GetMapping("/top-courses")
    @RequirePermission("stats:view")
    public ApiResponse<List<StatsDtos.TopCourseVO>> topCourses(
            @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(statsService.topCourses(limit));
    }
}
