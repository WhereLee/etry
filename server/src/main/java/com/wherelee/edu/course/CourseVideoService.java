package com.wherelee.edu.course;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.course.dto.CourseDtos;
import com.wherelee.edu.storage.FileStorage;
import com.wherelee.edu.teacher.TeacherProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 视频直传签名。
 *
 * 后端只负责：校验老师身份、校验类型和大小、生成对象键、发一个预签名 PUT。
 * 视频字节由浏览器直接传到对象存储，不经过服务器——
 * 这个项目的服务器出口只有 3Mbps，走中转传一个 300MB 的课时要十几分钟。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseVideoService {

    /** 单课时上限 500MB：45 分钟 720p 大约 300–500MB，再大就该转码或分片了 */
    private static final long MAX_VIDEO_BYTES = 500L * 1024 * 1024;
    private static final String CONTENT_TYPE = "video/mp4";
    /** 签名有效期：家用宽带上行 47Mbps，500MB 约需 90 秒，30 分钟留足余量 */
    private static final Duration UPLOAD_TTL = Duration.ofMinutes(30);

    private final FileStorage fileStorage;
    private final CourseTeacherService teacherService;

    public CourseDtos.VideoTicketVO ticket(Long userId, CourseDtos.VideoTicketRequest request) {
        TeacherProfile profile = teacherService.requireApprovedTeacher(userId);

        if (!CONTENT_TYPE.equalsIgnoreCase(request.contentType())) {
            throw BizException.of(ErrorCode.VIDEO_TYPE_INVALID);
        }
        if (request.sizeBytes() > MAX_VIDEO_BYTES) {
            throw new BizException(ErrorCode.INVALID_PARAM, "单个视频不能超过 500MB");
        }

        // 按讲师分目录：出问题能按人追溯，将来也能按人统计存储用量
        String key = fileStorage.newKey("video/" + profile.getId(), "mp4");
        String uploadUrl = fileStorage.presignedPutUrl(key, CONTENT_TYPE, UPLOAD_TTL);
        log.info("视频上传签名 teacherId={} key={} size={}", profile.getId(), key, request.sizeBytes());
        return new CourseDtos.VideoTicketVO(key, uploadUrl, (int) UPLOAD_TTL.toSeconds());
    }
}
