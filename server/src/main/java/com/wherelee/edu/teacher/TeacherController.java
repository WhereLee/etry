package com.wherelee.edu.teacher;

import com.wherelee.edu.audit.AuditLog;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.security.CurrentUser;
import com.wherelee.edu.teacher.dto.TeacherApplyRequest;
import com.wherelee.edu.teacher.dto.TeacherProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;

    @PostMapping("/api/teacher/apply")
    @AuditLog(action = "TEACHER_APPLY", targetType = "TEACHER")
    public ApiResponse<TeacherProfileVO> apply(@CurrentUser Long userId,
                                               @Valid @RequestBody TeacherApplyRequest request) {
        return ApiResponse.ok(teacherService.apply(userId, request));
    }

    @GetMapping("/api/teacher/profile")
    public ApiResponse<TeacherProfileVO> myProfile(@CurrentUser Long userId) {
        return ApiResponse.ok(teacherService.myProfile(userId));
    }

    @PutMapping("/api/teacher/profile")
    @AuditLog(action = "TEACHER_PROFILE_UPDATE", targetType = "TEACHER")
    public ApiResponse<Void> updateProfile(@CurrentUser Long userId,
                                           @Valid @RequestBody TeacherApplyRequest request) {
        teacherService.updateProfile(userId, request);
        return ApiResponse.ok();
    }

    /** 讲师本人换头像。写入讲师档案，与账号头像互不影响 */
    @PostMapping("/api/teacher/avatar")
    @AuditLog(action = "TEACHER_AVATAR_UPLOAD", targetType = "TEACHER")
    public ApiResponse<TeacherProfileVO> uploadAvatar(@CurrentUser Long userId,
                                                      @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(teacherService.uploadMyAvatar(userId, file));
    }

    /** 对外可见的讲师详情，只有审核通过的才会返回 */
    @GetMapping("/api/teachers/{id}")
    public ApiResponse<TeacherProfileVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(teacherService.publicDetail(id));
    }
}
