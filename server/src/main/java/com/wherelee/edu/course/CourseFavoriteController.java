package com.wherelee.edu.course;

import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.course.dto.CourseDtos;
import com.wherelee.edu.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课程收藏。必须登录——收藏是个人数据。
 */
@RestController
@RequiredArgsConstructor
public class CourseFavoriteController {

    private final CourseFavoriteService favoriteService;

    @PostMapping("/api/courses/{id}/favorite")
    public ApiResponse<Void> add(@CurrentUser Long userId, @PathVariable Long id) {
        favoriteService.add(userId, id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/api/courses/{id}/favorite")
    public ApiResponse<Void> remove(@CurrentUser Long userId, @PathVariable Long id) {
        favoriteService.remove(userId, id);
        return ApiResponse.ok();
    }

    @GetMapping("/api/favorites")
    public ApiResponse<PageResult<CourseDtos.CourseSummaryVO>> page(@CurrentUser Long userId,
                                                                    @RequestParam(defaultValue = "1") int page,
                                                                    @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(favoriteService.page(userId, page, size));
    }
}
