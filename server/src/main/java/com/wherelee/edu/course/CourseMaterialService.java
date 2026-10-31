package com.wherelee.edu.course;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.course.dto.MaterialDtos;
import com.wherelee.edu.storage.FileStorage;
import com.wherelee.edu.teacher.TeacherProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 课程课件资料。
 *
 * 和视频走同一套直传：后端只发预签名 PUT，字节由浏览器直接传到对象存储，
 * 传完再回来登记一条元信息。
 *
 * 两条硬性校验：
 *   1. 对象键必须属于当前讲师的 material/{profileId}/ 目录（防引用别人的文件）
 *   2. 文件大小从对象存储读取，不信客户端上报
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseMaterialService {

    private static final long MAX_BYTES = 50L * 1024 * 1024;
    private static final Duration UPLOAD_TTL = Duration.ofMinutes(30);

    /** 只放这几类文档：讲义、大纲、素材包，不放可执行文件和脚本 */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "application/pdf", "pdf",
            "application/msword", "doc",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx",
            "application/vnd.ms-powerpoint", "ppt",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx",
            "application/zip", "zip",
            "application/x-zip-compressed", "zip"
    );

    private final CourseMaterialMapper materialMapper;
    private final CourseMapper courseMapper;
    private final CourseTeacherService teacherService;
    private final FileStorage fileStorage;

    public MaterialDtos.TicketVO ticket(Long userId, MaterialDtos.TicketRequest request) {
        TeacherProfile profile = teacherService.requireApprovedTeacher(userId);

        String contentType = request.contentType().toLowerCase();
        String extension = ALLOWED_TYPES.get(contentType);
        if (extension == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "资料只支持 PDF / Word / PPT / ZIP");
        }
        if (request.sizeBytes() > MAX_BYTES) {
            throw new BizException(ErrorCode.INVALID_PARAM, "单个资料不能超过 50MB");
        }

        String key = fileStorage.newKey("material/" + profile.getId(), extension);
        String uploadUrl = fileStorage.presignedPutUrl(key, contentType, UPLOAD_TTL);
        return new MaterialDtos.TicketVO(key, uploadUrl, (int) UPLOAD_TTL.toSeconds());
    }

    @Transactional
    public Long add(Long userId, Long courseId, MaterialDtos.SaveRequest request) {
        TeacherProfile profile = teacherService.requireApprovedTeacher(userId);
        requireOwnCourse(profile, courseId);

        String fileKey = request.fileKey().trim();
        if (!fileStorage.ownsKey(fileKey, "material/" + profile.getId())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "文件不属于当前讲师");
        }
        long size = fileStorage.size(fileKey);
        if (size <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAM, "文件不存在或为空，请先完成上传");
        }

        CourseMaterial material = new CourseMaterial();
        material.setCourseId(courseId);
        material.setTitle(request.title().trim());
        material.setFileName(request.fileName().trim());
        material.setFileKey(fileKey);
        material.setSizeBytes(size);
        material.setSort(request.sort() == null ? 0 : request.sort());
        materialMapper.insert(material);

        log.info("课件登记 courseId={} materialId={} size={}", courseId, material.getId(), size);
        return material.getId();
    }

    @Transactional
    public void delete(Long userId, Long materialId) {
        TeacherProfile profile = teacherService.requireApprovedTeacher(userId);
        CourseMaterial material = materialMapper.findById(materialId);
        if (material == null) {
            throw BizException.of(ErrorCode.MATERIAL_NOT_FOUND);
        }
        requireOwnCourse(profile, material.getCourseId());

        // 先删对象再删记录：删对象失败不影响记录，反过来会留下孤儿文件
        fileStorage.delete(material.getFileKey());
        materialMapper.deleteById(materialId);
        log.info("课件删除 materialId={}", materialId);
    }

    /** 学员视角：课程必须已上架 */
    public List<MaterialDtos.MaterialVO> listForLearner(Long courseId) {
        Course course = courseMapper.findById(courseId);
        if (course == null || !Course.STATUS_PUBLISHED.equals(course.getStatus())) {
            throw BizException.of(ErrorCode.COURSE_NOT_FOUND);
        }
        return toVOs(materialMapper.findByCourseId(courseId));
    }

    /** 老师视角：不限课程状态，草稿课也能先准备资料 */
    public List<MaterialDtos.MaterialVO> listForTeacher(Long userId, Long courseId) {
        TeacherProfile profile = teacherService.requireApprovedTeacher(userId);
        requireOwnCourse(profile, courseId);
        return toVOs(materialMapper.findByCourseId(courseId));
    }

    /** 课程删除时连带清理 */
    @Transactional
    public void deleteByCourse(Long courseId) {
        for (CourseMaterial material : materialMapper.findByCourseId(courseId)) {
            fileStorage.delete(material.getFileKey());
        }
        materialMapper.deleteByCourse(courseId);
    }

    private List<MaterialDtos.MaterialVO> toVOs(List<CourseMaterial> materials) {
        return materials.stream()
                .map(m -> new MaterialDtos.MaterialVO(
                        m.getId(),
                        m.getTitle(),
                        m.getFileName(),
                        m.getSizeBytes() == null ? 0L : m.getSizeBytes(),
                        // 预签名 GET，默认 2 小时；文件命名由前端用 fileName 处理
                        fileStorage.url(m.getFileKey()),
                        m.getCreatedAt()))
                .toList();
    }

    private void requireOwnCourse(TeacherProfile profile, Long courseId) {
        Course course = courseMapper.findById(courseId);
        if (course == null || !course.getTeacherId().equals(profile.getId())) {
            throw BizException.of(ErrorCode.COURSE_NOT_FOUND);
        }
    }
}
