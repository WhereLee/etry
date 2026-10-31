package com.wherelee.edu.course;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.course.dto.CourseDtos;
import com.wherelee.edu.storage.FileStorage;
import com.wherelee.edu.storage.Mp4DurationReader;
import com.wherelee.edu.teacher.TeacherProfile;
import com.wherelee.edu.teacher.TeacherProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 老师侧：建课、排章节、排课时、提交审核。
 *
 * 所有操作都先确认「这个人有没有通过审核的讲师档案」，
 * 再由档案反查课程归属——课程属于讲师身份，不属于账号。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseTeacherService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE = 10_000;

    private final CourseMapper courseMapper;
    private final CourseChapterMapper chapterMapper;
    private final CourseLessonMapper lessonMapper;
    private final CourseMaterialMapper materialMapper;
    private final CourseTagMapper courseTagMapper;
    private final TagMapper tagMapper;
    private final TeacherProfileMapper profileMapper;
    private final CourseAssembler assembler;
    private final FileStorage fileStorage;
    private final Mp4DurationReader mp4DurationReader;

    // ------------------------------------------------------------------ 课程

    @Transactional
    public Long create(Long userId, CourseDtos.SaveRequest request) {
        TeacherProfile profile = requireApprovedTeacher(userId);
        requireOwnCoverKey(profile, request.coverKey());

        Course course = new Course();
        course.setTeacherId(profile.getId());
        course.setSubjectId(request.subjectId());
        course.setLevel(StringUtils.hasText(request.level()) ? request.level().trim() : null);
        course.setSequential(Boolean.TRUE.equals(request.sequential()));
        course.setTitle(request.title().trim());
        course.setSummary(request.summary());
        course.setDescription(request.description());
        course.setCoverKey(request.coverKey());
        course.setStatus(Course.STATUS_DRAFT);
        courseMapper.insert(course);
        saveTags(course.getId(), request.tagIds());

        log.info("课程创建 courseId={} teacherId={}", course.getId(), profile.getId());
        return course.getId();
    }

    @Transactional
    public void update(Long userId, Long courseId, CourseDtos.SaveRequest request) {
        TeacherProfile profile = requireApprovedTeacher(userId);
        Course course = requireOwnCourse(profile, courseId);
        if (!course.isEditable()) {
            throw new BizException(ErrorCode.COURSE_STATUS_INVALID, "只有草稿、被驳回或已下架的课程可以修改");
        }
        requireOwnCoverKey(profile, request.coverKey());
        course.setSubjectId(request.subjectId());
        course.setLevel(StringUtils.hasText(request.level()) ? request.level().trim() : null);
        course.setSequential(Boolean.TRUE.equals(request.sequential()));
        course.setTitle(request.title().trim());
        course.setSummary(request.summary());
        course.setDescription(request.description());
        course.setCoverKey(request.coverKey());
        courseMapper.update(course);
        log.info("课程修改 courseId={}", courseId);
    }

    @Transactional
    public void delete(Long userId, Long courseId) {
        Course course = requireOwnCourse(userId, courseId);
        // 上架过的课可能已经有人学过，删了就是把别人看过的内容删了
        if (!course.isDeletable()) {
            throw new BizException(ErrorCode.COURSE_STATUS_INVALID, "上架过的课程不能删除，只能下架");
        }
        // 先清子表，再删课程；视频与资料对象一并清掉，避免对象存储里堆垃圾
        for (CourseLesson lesson : lessonMapper.findByCourseId(courseId)) {
            fileStorage.delete(lesson.getVideoKey());
        }
        for (CourseMaterial material : materialMapper.findByCourseId(courseId)) {
            fileStorage.delete(material.getFileKey());
        }
        lessonMapper.deleteByCourseId(courseId);
        materialMapper.deleteByCourse(courseId);
        courseTagMapper.deleteByCourse(courseId);
        chapterMapper.deleteByCourseId(courseId);
        courseMapper.deleteById(courseId);
        log.info("课程删除 courseId={}", courseId);
    }

    /**
     * 提交审核。至少要有「一个有视频的课时」，
     * 否则审出来是一张空课，学员点进去什么都没有。
     */
    @Transactional
    public void submit(Long userId, Long courseId) {
        Course course = requireOwnCourse(userId, courseId);
        if (!course.isSubmittable()) {
            throw new BizException(ErrorCode.COURSE_STATUS_INVALID, "当前状态不能提交审核");
        }
        boolean hasVideo = lessonMapper.findByCourseId(courseId).stream()
                .anyMatch(lesson -> lesson.getVideoKey() != null && !lesson.getVideoKey().isBlank());
        if (!hasVideo) {
            throw new BizException(ErrorCode.INVALID_PARAM, "至少上传一个课时视频后才能提交审核");
        }
        courseMapper.updateStatus(courseId, Course.STATUS_PENDING, null, null);
        log.info("课程提交审核 courseId={}", courseId);
    }

    /**
     * 老师自己下架。下架是收回自己的内容，不影响任何人，不需要管理员审批。
     * 下架后才能改内容，改完重新提交审核。
     */
    @Transactional
    public void offline(Long userId, Long courseId) {
        Course course = requireOwnCourse(userId, courseId);
        if (!Course.STATUS_PUBLISHED.equals(course.getStatus())) {
            throw new BizException(ErrorCode.COURSE_STATUS_INVALID, "只有已上架的课程可以下架");
        }
        courseMapper.updateStatus(courseId, Course.STATUS_OFFLINE, null, course.getPublishedAt());
        log.info("老师下架课程 courseId={}", courseId);
    }

    public PageResult<CourseDtos.CourseSummaryVO> page(Long userId, String status, int page, int size) {
        TeacherProfile profile = requireApprovedTeacher(userId);
        int safePage = Math.min(Math.max(page, 1), MAX_PAGE);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        long total = courseMapper.countByTeacher(profile.getId(), status);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<CourseDtos.CourseSummaryVO> records = assembler.summaries(
                courseMapper.findPageByTeacher(profile.getId(), status, (safePage - 1) * safeSize, safeSize));
        return PageResult.of(records, total, safePage, safeSize);
    }

    public CourseDtos.CourseDetailVO detail(Long userId, Long courseId) {
        return assembler.detail(requireOwnCourse(userId, courseId), userId);
    }

    // ------------------------------------------------------------------ 章节

    @Transactional
    public Long addChapter(Long userId, Long courseId, CourseDtos.ChapterSaveRequest request) {
        Course course = requireOwnCourse(userId, courseId);
        requireEditable(course);

        CourseChapter chapter = new CourseChapter();
        chapter.setCourseId(courseId);
        chapter.setTitle(request.title().trim());
        chapter.setSort(request.sort() == null ? 0 : request.sort());
        chapterMapper.insert(chapter);
        return chapter.getId();
    }

    @Transactional
    public void updateChapter(Long userId, Long chapterId, CourseDtos.ChapterSaveRequest request) {
        CourseChapter chapter = requireChapter(chapterId);
        requireEditable(requireOwnCourse(userId, chapter.getCourseId()));

        chapter.setTitle(request.title().trim());
        chapter.setSort(request.sort() == null ? chapter.getSort() : request.sort());
        chapterMapper.update(chapter);
    }

    /** 删章节连带删掉它的课时和视频，不留孤儿数据 */
    @Transactional
    public void deleteChapter(Long userId, Long chapterId) {
        CourseChapter chapter = requireChapter(chapterId);
        requireEditable(requireOwnCourse(userId, chapter.getCourseId()));

        for (CourseLesson lesson : lessonMapper.findByChapterId(chapterId)) {
            fileStorage.delete(lesson.getVideoKey());
        }
        lessonMapper.deleteByChapterId(chapterId);
        chapterMapper.deleteById(chapterId);
        log.info("章节删除 chapterId={}", chapterId);
    }

    // ------------------------------------------------------------------ 课时

    @Transactional
    public Long addLesson(Long userId, Long chapterId, CourseDtos.LessonSaveRequest request) {
        TeacherProfile profile = requireApprovedTeacher(userId);
        CourseChapter chapter = requireChapter(chapterId);
        Course course = requireOwnCourse(profile, chapter.getCourseId());
        requireEditable(course);
        requireOwnVideoKey(profile, request.videoKey());

        CourseLesson lesson = new CourseLesson();
        lesson.setCourseId(course.getId());
        lesson.setChapterId(chapterId);
        lesson.setTitle(request.title().trim());
        lesson.setVideoKey(StringUtils.hasText(request.videoKey()) ? request.videoKey().trim() : null);
        lesson.setDurationSeconds(resolveDuration(request.videoKey()));
        lesson.setSizeBytes(resolveSize(request.videoKey()));
        lesson.setSort(request.sort() == null ? 0 : request.sort());
        lessonMapper.insert(lesson);
        log.info("课时创建 lessonId={} courseId={}", lesson.getId(), course.getId());
        return lesson.getId();
    }

    @Transactional
    public void updateLesson(Long userId, Long lessonId, CourseDtos.LessonSaveRequest request) {
        TeacherProfile profile = requireApprovedTeacher(userId);
        CourseLesson lesson = requireLesson(lessonId);
        Course course = requireOwnCourse(profile, lesson.getCourseId());
        requireEditable(course);
        requireOwnVideoKey(profile, request.videoKey());

        lesson.setTitle(request.title().trim());
        lesson.setSort(request.sort() == null ? lesson.getSort() : request.sort());
        lessonMapper.update(lesson);

        // 空串按「没换视频」处理：前端表单没填这一项时传的就是空串，
        // 若当成换视频，会把已有视频清空并把对象存储里的文件删掉
        String newKey = StringUtils.hasText(request.videoKey()) ? request.videoKey().trim() : null;
        if (newKey != null && !newKey.equals(lesson.getVideoKey())) {
            String oldKey = lesson.getVideoKey();
            lessonMapper.updateVideo(lessonId, newKey, resolveDuration(newKey), resolveSize(newKey));
            fileStorage.delete(oldKey);
        }
    }

    @Transactional
    public void deleteLesson(Long userId, Long lessonId) {
        CourseLesson lesson = requireLesson(lessonId);
        requireEditable(requireOwnCourse(userId, lesson.getCourseId()));

        fileStorage.delete(lesson.getVideoKey());
        lessonMapper.deleteById(lessonId);
        log.info("课时删除 lessonId={}", lessonId);
    }

    // ------------------------------------------------------------------ 内部

    /** 老师侧的统一入口校验：必须有通过审核的讲师档案 */
    public TeacherProfile requireApprovedTeacher(Long userId) {
        TeacherProfile profile = profileMapper.findByUserId(userId);
        if (profile == null || !TeacherProfile.STATUS_APPROVED.equals(profile.getStatus())) {
            throw BizException.of(ErrorCode.TEACHER_REQUIRED);
        }
        return profile;
    }

    private Course requireOwnCourse(Long userId, Long courseId) {
        TeacherProfile profile = requireApprovedTeacher(userId);
        return requireOwnCourse(profile, courseId);
    }

    /** 已经拿到档案时用这个，避免同一个请求里重复查一次讲师档案 */
    private Course requireOwnCourse(TeacherProfile profile, Long courseId) {
        Course course = courseMapper.findById(courseId);
        if (course == null) {
            throw BizException.of(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!course.getTeacherId().equals(profile.getId())) {
            // 不暴露「课程存在但不属于你」，统一按不存在处理
            throw BizException.of(ErrorCode.COURSE_NOT_FOUND);
        }
        return course;
    }

    /**
     * 校验视频对象归属。
     * key 是前端传回的，不校验的话 A 只要拿到 B 目录下的对象键就能把它写进自己的课时。
     */
    private void requireOwnVideoKey(TeacherProfile profile, String videoKey) {
        if (!StringUtils.hasText(videoKey)) {
            return;
        }
        if (!fileStorage.ownsKey(videoKey, "video/" + profile.getId())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "视频不属于当前讲师");
        }
    }

    private void requireOwnCoverKey(TeacherProfile profile, String coverKey) {
        if (!StringUtils.hasText(coverKey)) {
            return;
        }
        if (!fileStorage.ownsKey(coverKey, "cover/" + profile.getId())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "封面不属于当前讲师");
        }
    }

    /**
     * 时长以文件为准，不用前端传的。
     * 解析不出来就拒绝，而不是当 0：时长为 0 会让完成判定永远不成立，
     * 学员看完了进度也不动，比报错更难排查。
     */
    private int resolveDuration(String videoKey) {
        if (!StringUtils.hasText(videoKey)) {
            return 0;
        }
        Integer seconds = mp4DurationReader.read(fileStorage, videoKey);
        if (seconds == null || seconds <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAM,
                    "无法解析视频时长，请上传标准 mp4 文件");
        }
        return seconds;
    }

    private long resolveSize(String videoKey) {
        return StringUtils.hasText(videoKey) ? fileStorage.size(videoKey) : 0L;
    }

    /**
     * 标签整体覆盖：null 表示「本次不改」，空列表表示「清空」。
     * 先删后插，让前端不用算差集。
     */
    private void saveTags(Long courseId, List<Long> tagIds) {
        if (tagIds == null) {
            return;
        }
        List<Long> valid = tagIds.stream().filter(Objects::nonNull).distinct().toList();
        for (Long tagId : valid) {
            if (tagMapper.findById(tagId) == null) {
                throw new BizException(ErrorCode.INVALID_PARAM, "标签不存在: " + tagId);
            }
        }
        courseTagMapper.deleteByCourse(courseId);
        if (!valid.isEmpty()) {
            courseTagMapper.batchInsert(courseId, valid);
        }
    }

    private CourseLesson requireLesson(Long lessonId) {
        CourseLesson lesson = lessonMapper.findById(lessonId);
        if (lesson == null) {
            throw BizException.of(ErrorCode.LESSON_NOT_FOUND);
        }
        return lesson;
    }

    private CourseChapter requireChapter(Long chapterId) {
        CourseChapter chapter = chapterMapper.findById(chapterId);
        if (chapter == null) {
            throw BizException.of(ErrorCode.CHAPTER_NOT_FOUND);
        }
        return chapter;
    }

    private void requireEditable(Course course) {
        if (!course.isEditable()) {
            throw new BizException(ErrorCode.COURSE_STATUS_INVALID, "只有草稿或被驳回的课程可以编辑");
        }
    }
}
