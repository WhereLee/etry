package com.wherelee.edu.course;

import com.wherelee.edu.course.dto.CourseDtos;
import com.wherelee.edu.storage.FileStorage;
import com.wherelee.edu.teacher.TeacherProfile;
import com.wherelee.edu.teacher.TeacherProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 把 Course / Chapter / Lesson 拼成对外 VO。
 *
 * 老师侧、后台、前台三处都要拼这两种结构，逻辑放一处，避免三份各写一遍以后改漏。
 */
@Component
@RequiredArgsConstructor
public class CourseAssembler {

    private final TeacherProfileMapper profileMapper;
    private final CourseChapterMapper chapterMapper;
    private final CourseLessonMapper lessonMapper;
    private final FileStorage fileStorage;

    public CourseDtos.CourseSummaryVO summary(Course course) {
        return new CourseDtos.CourseSummaryVO(
                course.getId(),
                course.getTitle(),
                course.getSummary(),
                fileStorage.url(course.getCoverKey()),
                course.getStatus(),
                course.getRejectReason(),
                teacherName(course.getTeacherId()),
                course.getSubjectId(),
                lessonMapper.countByCourse(course.getId()),
                course.getPublishedAt(),
                course.getUpdatedAt());
    }

    /** 详情带章节课时。课时里只给「有没有视频」，不给 videoKey */
    public CourseDtos.CourseDetailVO detail(Course course) {
        List<CourseChapter> chapters = chapterMapper.findByCourseId(course.getId());
        List<CourseLesson> lessons = lessonMapper.findByCourseId(course.getId());
        Map<Long, List<CourseLesson>> byChapter = lessons.stream()
                .collect(Collectors.groupingBy(CourseLesson::getChapterId));

        List<CourseDtos.ChapterVO> chapterVOs = chapters.stream()
                .map(chapter -> new CourseDtos.ChapterVO(
                        chapter.getId(),
                        chapter.getTitle(),
                        chapter.getSort(),
                        byChapter.getOrDefault(chapter.getId(), List.of()).stream()
                                .map(this::lessonVO).toList()))
                .toList();

        return new CourseDtos.CourseDetailVO(
                course.getId(),
                course.getTeacherId(),
                teacherName(course.getTeacherId()),
                course.getSubjectId(),
                course.getTitle(),
                course.getSummary(),
                course.getDescription(),
                fileStorage.url(course.getCoverKey()),
                course.getStatus(),
                course.getRejectReason(),
                course.getPublishedAt(),
                course.getCreatedAt(),
                chapterVOs);
    }

    public CourseDtos.LessonVO lessonVO(CourseLesson lesson) {
        String key = lesson.getVideoKey();
        return new CourseDtos.LessonVO(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getSort(),
                lesson.getDurationSeconds(),
                key != null && !key.isBlank());
    }

    /** 讲师对外展示用真实姓名；档案被删了也不能让课程列表崩掉 */
    private String teacherName(Long teacherId) {
        TeacherProfile profile = profileMapper.findById(teacherId);
        return profile == null ? null : profile.getRealName();
    }
}
