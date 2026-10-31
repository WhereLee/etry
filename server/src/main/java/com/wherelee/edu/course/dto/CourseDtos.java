package com.wherelee.edu.course.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class CourseDtos {

    /** 课程基本信息。封面存对象键，对外返回时由服务层转成可访问 URL */
    public record SaveRequest(

            @NotBlank(message = "课程标题不能为空")
            @Size(max = 100, message = "标题不能超过 100 字")
            String title,

            @Size(max = 200, message = "简介不能超过 200 字")
            String summary,

            @Size(max = 20000, message = "详情不能超过 20000 字")
            String description,

            Long subjectId,

            String coverKey
    ) {
    }

    public record ChapterSaveRequest(

            @NotBlank(message = "章节标题不能为空")
            @Size(max = 100, message = "标题不能超过 100 字")
            String title,

            Integer sort
    ) {
    }

    /** 建课时视频可以为空：允许先排目录、后补视频 */
    public record LessonSaveRequest(

            @NotBlank(message = "课时标题不能为空")
            @Size(max = 100, message = "标题不能超过 100 字")
            String title,

            Integer sort,

            String videoKey,

            Integer durationSeconds,

            Long sizeBytes
    ) {
    }

    public record ReviewRequest(

            @NotNull(message = "必须明确通过或驳回")
            Boolean approved,

            @Size(max = 200, message = "驳回原因不能超过 200 字")
            String rejectReason
    ) {
    }

    /** 申请视频直传地址 */
    public record VideoTicketRequest(

            @NotBlank(message = "文件名不能为空")
            String fileName,

            @NotBlank(message = "文件类型不能为空")
            String contentType,

            @NotNull(message = "文件大小不能为空")
            @Min(value = 1, message = "文件大小必须大于 0")
            Long sizeBytes
    ) {
    }

    public record VideoTicketVO(String key, String uploadUrl, int expiresInSeconds) {
    }

    public record PlayVO(Long lessonId, String title, String playUrl) {
    }

    /** 列表项：不含章节课时，避免列表接口拉一大堆数据 */
    public record CourseSummaryVO(
            Long id,
            String title,
            String summary,
            String coverUrl,
            String status,
            String rejectReason,
            String teacherName,
            Long subjectId,
            long lessonCount,
            LocalDateTime publishedAt,
            LocalDateTime updatedAt
    ) {
    }

    /**
     * 课时项。不返回 videoKey：
     * 老师侧不需要它，前台更不能拿到——拿到就等于绕过播放接口直接访问对象。
     */
    public record LessonVO(
            Long id,
            String title,
            Integer sort,
            Integer durationSeconds,
            boolean hasVideo
    ) {
    }

    public record ChapterVO(Long id, String title, Integer sort, List<LessonVO> lessons) {
    }

    public record CourseDetailVO(
            Long id,
            Long teacherId,
            String teacherName,
            Long subjectId,
            String title,
            String summary,
            String description,
            String coverUrl,
            String status,
            String rejectReason,
            LocalDateTime publishedAt,
            LocalDateTime createdAt,
            List<ChapterVO> chapters
    ) {
    }
}
