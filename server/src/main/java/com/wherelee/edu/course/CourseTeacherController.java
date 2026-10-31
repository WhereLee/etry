package com.wherelee.edu.course;

import com.wherelee.edu.audit.AuditLog;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.course.dto.CourseDtos;
import com.wherelee.edu.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 老师侧：课程、章节、课时、视频签名。
 *
 * 这里不挂 @RequirePermission —— 能不能建课由「有没有通过审核的讲师档案」决定，
 * 在 Service 里统一校验。角色和权限点是用来管后台的，老师身份是另一回事。
 */
@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class CourseTeacherController {

    private final CourseTeacherService courseTeacherService;
    private final CourseVideoService courseVideoService;

    // ---------------------------------------------------------------- 课程

    @PostMapping("/courses")
    @AuditLog(action = "COURSE_CREATE", targetType = "COURSE")
    public ApiResponse<Long> create(@CurrentUser Long userId,
                                    @Valid @RequestBody CourseDtos.SaveRequest request) {
        return ApiResponse.ok(courseTeacherService.create(userId, request));
    }

    @GetMapping("/courses")
    public ApiResponse<PageResult<CourseDtos.CourseSummaryVO>> page(
            @CurrentUser Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(courseTeacherService.page(userId, status, page, size));
    }

    @GetMapping("/courses/{id}")
    public ApiResponse<CourseDtos.CourseDetailVO> detail(@CurrentUser Long userId,
                                                         @PathVariable Long id) {
        return ApiResponse.ok(courseTeacherService.detail(userId, id));
    }

    @PutMapping("/courses/{id}")
    public ApiResponse<Void> update(@CurrentUser Long userId,
                                    @PathVariable Long id,
                                    @Valid @RequestBody CourseDtos.SaveRequest request) {
        courseTeacherService.update(userId, id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/courses/{id}")
    @AuditLog(action = "COURSE_DELETE", targetType = "COURSE", targetIdIndex = 1)
    public ApiResponse<Void> delete(@CurrentUser Long userId, @PathVariable Long id) {
        courseTeacherService.delete(userId, id);
        return ApiResponse.ok();
    }

    @PutMapping("/courses/{id}/submit")
    @AuditLog(action = "COURSE_SUBMIT", targetType = "COURSE", targetIdIndex = 1)
    public ApiResponse<Void> submit(@CurrentUser Long userId, @PathVariable Long id) {
        courseTeacherService.submit(userId, id);
        return ApiResponse.ok();
    }

    /** 自己下架：下架后才能改内容，改完重新提交审核 */
    @PutMapping("/courses/{id}/offline")
    @AuditLog(action = "COURSE_OFFLINE_BY_TEACHER", targetType = "COURSE", targetIdIndex = 1)
    public ApiResponse<Void> offline(@CurrentUser Long userId, @PathVariable Long id) {
        courseTeacherService.offline(userId, id);
        return ApiResponse.ok();
    }

    // ---------------------------------------------------------------- 章节

    @PostMapping("/courses/{id}/chapters")
    public ApiResponse<Long> addChapter(@CurrentUser Long userId,
                                        @PathVariable Long id,
                                        @Valid @RequestBody CourseDtos.ChapterSaveRequest request) {
        return ApiResponse.ok(courseTeacherService.addChapter(userId, id, request));
    }

    @PutMapping("/chapters/{id}")
    public ApiResponse<Void> updateChapter(@CurrentUser Long userId,
                                           @PathVariable Long id,
                                           @Valid @RequestBody CourseDtos.ChapterSaveRequest request) {
        courseTeacherService.updateChapter(userId, id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/chapters/{id}")
    public ApiResponse<Void> deleteChapter(@CurrentUser Long userId, @PathVariable Long id) {
        courseTeacherService.deleteChapter(userId, id);
        return ApiResponse.ok();
    }

    // ---------------------------------------------------------------- 课时

    @PostMapping("/chapters/{id}/lessons")
    public ApiResponse<Long> addLesson(@CurrentUser Long userId,
                                       @PathVariable Long id,
                                       @Valid @RequestBody CourseDtos.LessonSaveRequest request) {
        return ApiResponse.ok(courseTeacherService.addLesson(userId, id, request));
    }

    @PutMapping("/lessons/{id}")
    public ApiResponse<Void> updateLesson(@CurrentUser Long userId,
                                          @PathVariable Long id,
                                          @Valid @RequestBody CourseDtos.LessonSaveRequest request) {
        courseTeacherService.updateLesson(userId, id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/lessons/{id}")
    public ApiResponse<Void> deleteLesson(@CurrentUser Long userId, @PathVariable Long id) {
        courseTeacherService.deleteLesson(userId, id);
        return ApiResponse.ok();
    }

    // ---------------------------------------------------------------- 视频

    /** 申请直传签名：拿到 uploadUrl 后前端直接 PUT 到对象存储，字节不经过服务器 */
    @PostMapping("/videos/ticket")
    public ApiResponse<CourseDtos.VideoTicketVO> videoTicket(
            @CurrentUser Long userId,
            @Valid @RequestBody CourseDtos.VideoTicketRequest request) {
        return ApiResponse.ok(courseVideoService.ticket(userId, request));
    }
}
