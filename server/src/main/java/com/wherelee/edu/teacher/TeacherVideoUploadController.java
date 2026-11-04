package com.wherelee.edu.teacher;

import com.wherelee.edu.aop.AdminAudit;
import com.wherelee.edu.aop.Measured;
import com.wherelee.edu.aop.RateLimit;
import com.wherelee.edu.aop.RequireRole;
import com.wherelee.edu.teacher.dto.TeacherVideoUploadDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher")
public class TeacherVideoUploadController {

    private final TeacherVideoUploadService service;

    public TeacherVideoUploadController(TeacherVideoUploadService service) {
        this.service = service;
    }

    @PostMapping("/series/{seriesId}/chapters/{chapterId}/videos/{videoId}/upload-url")
    @RequireRole("TEACHER")
    @RateLimit(key = "teacher:video:uploadUrl", limit = 30, windowSeconds = 60)
    @Measured("teacher.video.uploadUrl")
    @AdminAudit(action = "TEACHER_VIDEO_UPLOAD_URL", targetType = "VIDEO", targetIdArg = 3)
    public TeacherVideoUploadDtos.UploadUrlResponse uploadUrl(
            @RequestAttribute("userId") Long teacherId,
            @PathVariable Long seriesId,
            @PathVariable Long chapterId,
            @PathVariable Long videoId,
            @Valid @RequestBody TeacherVideoUploadDtos.UploadUrlRequest request) {
        return service.createUploadUrl(teacherId, seriesId, chapterId, videoId, request);
    }

    @PostMapping("/series/{seriesId}/chapters/{chapterId}/videos/{videoId}/complete")
    @RequireRole("TEACHER")
    @Measured("teacher.video.complete")
    @AdminAudit(action = "TEACHER_VIDEO_COMPLETE", targetType = "VIDEO", targetIdArg = 3)
    public TeacherVideoUploadDtos.CompleteResponse complete(
            @RequestAttribute("userId") Long teacherId,
            @PathVariable Long seriesId,
            @PathVariable Long chapterId,
            @PathVariable Long videoId,
            @Valid @RequestBody TeacherVideoUploadDtos.CompleteRequest request) {
        return service.complete(teacherId, seriesId, chapterId, videoId, request);
    }
}
