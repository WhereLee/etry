package com.wherelee.edu.teacher;

import com.wherelee.edu.aop.AdminAudit;
import com.wherelee.edu.aop.Idempotent;
import com.wherelee.edu.aop.Measured;
import com.wherelee.edu.aop.RateLimit;
import com.wherelee.edu.aop.RequireRole;
import com.wherelee.edu.teacher.dto.TeacherContentDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher")
public class TeacherContentController {

    private final TeacherContentService service;

    public TeacherContentController(TeacherContentService service) {
        this.service = service;
    }

    @PostMapping("/series")
    @RequireRole("TEACHER")
    @RateLimit(key = "teacher:series:create", limit = 20, windowSeconds = 60)
    @Idempotent(key = "teacher:series:create", windowSeconds = 3)
    @Measured("teacher.series.create")
    @AdminAudit(action = "TEACHER_SERIES_CREATE", targetType = "SERIES",
            targetIdFromResult = true)
    public Long createSeries(@RequestAttribute("userId") Long teacherId,
                             @Valid @RequestBody TeacherContentDtos.SeriesRequest request) {
        return service.createSeries(teacherId, request);
    }

    @PutMapping("/series/{id}")
    @RequireRole("TEACHER")
    @Measured("teacher.series.update")
    @AdminAudit(action = "TEACHER_SERIES_UPDATE", targetType = "SERIES", targetIdArg = 1)
    public void updateSeries(@RequestAttribute("userId") Long teacherId,
                             @PathVariable Long id,
                             @Valid @RequestBody TeacherContentDtos.SeriesRequest request) {
        service.updateSeries(teacherId, id, request);
    }

    @PutMapping("/series/{id}/status")
    @RequireRole("TEACHER")
    @Measured("teacher.series.status")
    @AdminAudit(action = "TEACHER_SERIES_STATUS", targetType = "SERIES", targetIdArg = 1)
    public void updateStatus(@RequestAttribute("userId") Long teacherId,
                             @PathVariable Long id,
                             @Valid @RequestBody TeacherContentDtos.StatusRequest request) {
        service.updateStatus(teacherId, id, request);
    }

    @PostMapping("/series/{seriesId}/chapters")
    @RequireRole("TEACHER")
    @Idempotent(key = "teacher:chapter:create", windowSeconds = 3)
    @Measured("teacher.chapter.create")
    @AdminAudit(action = "TEACHER_CHAPTER_CREATE", targetType = "CHAPTER",
            targetIdFromResult = true)
    public Long createChapter(@RequestAttribute("userId") Long teacherId,
                              @PathVariable Long seriesId,
                              @Valid @RequestBody TeacherContentDtos.ChapterRequest request) {
        return service.createChapter(teacherId, seriesId, request);
    }

    @PutMapping("/series/{seriesId}/chapters/{chapterId}")
    @RequireRole("TEACHER")
    @Measured("teacher.chapter.update")
    @AdminAudit(action = "TEACHER_CHAPTER_UPDATE", targetType = "CHAPTER", targetIdArg = 2)
    public void updateChapter(@RequestAttribute("userId") Long teacherId,
                              @PathVariable Long seriesId,
                              @PathVariable Long chapterId,
                              @Valid @RequestBody TeacherContentDtos.ChapterRequest request) {
        service.updateChapter(teacherId, seriesId, chapterId, request);
    }

    @DeleteMapping("/series/{seriesId}/chapters/{chapterId}")
    @RequireRole("TEACHER")
    @Measured("teacher.chapter.delete")
    @AdminAudit(action = "TEACHER_CHAPTER_DELETE", targetType = "CHAPTER", targetIdArg = 2)
    public void deleteChapter(@RequestAttribute("userId") Long teacherId,
                              @PathVariable Long seriesId,
                              @PathVariable Long chapterId) {
        service.deleteChapter(teacherId, seriesId, chapterId);
    }

    @PostMapping("/series/{seriesId}/chapters/{chapterId}/videos")
    @RequireRole("TEACHER")
    @Idempotent(key = "teacher:video:create", windowSeconds = 3)
    @Measured("teacher.video.create")
    @AdminAudit(action = "TEACHER_VIDEO_CREATE", targetType = "VIDEO",
            targetIdFromResult = true)
    public Long createVideo(@RequestAttribute("userId") Long teacherId,
                            @PathVariable Long seriesId,
                            @PathVariable Long chapterId,
                            @Valid @RequestBody TeacherContentDtos.VideoRequest request) {
        return service.createVideo(teacherId, seriesId, chapterId, request);
    }

    @PutMapping("/series/{seriesId}/chapters/{chapterId}/videos/{videoId}")
    @RequireRole("TEACHER")
    @Measured("teacher.video.update")
    @AdminAudit(action = "TEACHER_VIDEO_UPDATE", targetType = "VIDEO", targetIdArg = 3)
    public void updateVideo(@RequestAttribute("userId") Long teacherId,
                            @PathVariable Long seriesId,
                            @PathVariable Long chapterId,
                            @PathVariable Long videoId,
                            @Valid @RequestBody TeacherContentDtos.VideoRequest request) {
        service.updateVideo(teacherId, seriesId, chapterId, videoId, request);
    }

    @DeleteMapping("/series/{seriesId}/chapters/{chapterId}/videos/{videoId}")
    @RequireRole("TEACHER")
    @Measured("teacher.video.delete")
    @AdminAudit(action = "TEACHER_VIDEO_DELETE", targetType = "VIDEO", targetIdArg = 3)
    public void deleteVideo(@RequestAttribute("userId") Long teacherId,
                            @PathVariable Long seriesId,
                            @PathVariable Long chapterId,
                            @PathVariable Long videoId) {
        service.deleteVideo(teacherId, seriesId, chapterId, videoId);
    }
}
