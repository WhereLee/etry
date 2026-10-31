package com.wherelee.edu.course;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.course.dto.CourseDtos;
import com.wherelee.edu.storage.FileStorage;
import com.wherelee.edu.teacher.TeacherProfile;
import com.wherelee.edu.teacher.TeacherProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    private final TeacherProfileMapper profileMapper;
    private final CourseAssembler assembler;
    private final FileStorage fileStorage;

    // ------------------------------------------------------------------ 课程

    @Transactional
    public Long create(Long userId, CourseDtos.SaveRequest request) {
        TeacherProfile profile = requireApprovedTeacher(userId);

        Course course = new Course();
        course.setTeacherId(profile.getId());
        course.setSubjectId(request.subjectId());
        course.setTitle(request.title().trim());
        course.setSummary(request.summary());
        course.setDescription(request.description());
        course.setCoverKey(request.coverKey());
        course.setStatus(Course.STATUS_DRAFT);
        courseMapper.insert(course);

        log.info("课程创建 courseId={} teacherId={}", course.getId(), profile.getId());
        return course.getId();
    }

    @Transactional
    public void update(Long userId, Long courseId, CourseDtos.SaveRequest request) {
        Course course = requireOwnCourse(userId, courseId);
        if (!course.isEditable()) {
            throw new BizException(ErrorCode.COURSE_STATUS_INVALID, "只有草稿或被驳回的课程可以修改");
        }
        course.setSubjectId(request.subjectId());
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
        if (!course.isEditable()) {
            throw new BizException(ErrorCode.COURSE_STATUS_INVALID, "只有草稿或被驳回的课程可以删除");
        }
        // 先清子表，再删课程；视频对象一并清掉，避免对象存储里堆垃圾
        for (CourseLesson lesson : lessonMapper.findByCourseId(courseId)) {
            fileStorage.delete(lesson.getVideoKey());
        }
        lessonMapper.deleteByCourseId(courseId);
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

    public PageResult<CourseDtos.CourseSummaryVO> page(Long userId, String status, int page, int size) {
        TeacherProfile profile = requireApprovedTeacher(userId);
        int safePage = Math.min(Math.max(page, 1), MAX_PAGE);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        long total = courseMapper.countByTeacher(profile.getId(), status);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<CourseDtos.CourseSummaryVO> records = courseMapper
                .findPageByTeacher(profile.getId(), status, (safePage - 1) * safeSize, safeSize)
                .stream().map(assembler::summary).toList();
        return PageResult.of(records, total, safePage, safeSize);
    }

    public CourseDtos.CourseDetailVO detail(Long userId, Long courseId) {
        return assembler.detail(requireOwnCourse(userId, courseId));
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
        CourseChapter chapter = requireChapter(chapterId);
        Course course = requireOwnCourse(userId, chapter.getCourseId());
        requireEditable(course);

        CourseLesson lesson = new CourseLesson();
        lesson.setCourseId(course.getId());
        lesson.setChapterId(chapterId);
        lesson.setTitle(request.title().trim());
        lesson.setVideoKey(request.videoKey());
        lesson.setDurationSeconds(request.durationSeconds() == null ? 0 : request.durationSeconds());
        lesson.setSizeBytes(request.sizeBytes() == null ? 0L : request.sizeBytes());
        lesson.setSort(request.sort() == null ? 0 : request.sort());
        lessonMapper.insert(lesson);
        log.info("课时创建 lessonId={} courseId={}", lesson.getId(), course.getId());
        return lesson.getId();
    }

    @Transactional
    public void updateLesson(Long userId, Long lessonId, CourseDtos.LessonSaveRequest request) {
        CourseLesson lesson = requireLesson(lessonId);
        Course course = requireOwnCourse(userId, lesson.getCourseId());
        requireEditable(course);

        lesson.setTitle(request.title().trim());
        lesson.setSort(request.sort() == null ? lesson.getSort() : request.sort());
        lessonMapper.update(lesson);

        // 换了视频就回填新信息，并把旧对象删掉
        if (request.videoKey() != null && !request.videoKey().equals(lesson.getVideoKey())) {
            String oldKey = lesson.getVideoKey();
            lessonMapper.updateVideo(lessonId, request.videoKey(),
                    request.durationSeconds() == null ? 0 : request.durationSeconds(),
                    request.sizeBytes() == null ? 0L : request.sizeBytes());
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
