package com.wherelee.edu.course;

import com.wherelee.edu.course.dto.CourseDtos;
import com.wherelee.edu.course.dto.TagDtos;
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
    private final CourseReviewMapper reviewMapper;
    private final CourseFavoriteMapper favoriteMapper;
    private final CourseTagMapper courseTagMapper;
    private final FileStorage fileStorage;

    /**
     * 列表页批量组装：课时数和讲师姓名各查一次，
     * 不再像之前那样每行一次 count + 一次按 id 查档案（一页 50 条就是 100 次 SQL）。
     */
    public List<CourseDtos.CourseSummaryVO> summaries(List<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return List.of();
        }
        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        List<Long> teacherIds = courses.stream().map(Course::getTeacherId).distinct().toList();

        Map<Long, Long> lessonCounts = lessonMapper.countByCourseIds(courseIds).stream()
                .collect(Collectors.toMap(CourseLessonCount::courseId, CourseLessonCount::cnt));
        Map<Long, TeacherProfile> teachers = profileMapper.findByIds(teacherIds);

        return courses.stream().map(course -> {
            TeacherProfile teacher = teachers.get(course.getTeacherId());
            return new CourseDtos.CourseSummaryVO(
                    course.getId(),
                    course.getTitle(),
                    course.getSummary(),
                    fileStorage.url(course.getCoverKey()),
                    course.getStatus(),
                    course.getRejectReason(),
                    teacher == null ? null : teacher.getRealName(),
                    course.getSubjectId(),
                    course.getLevel(),
                    lessonCounts.getOrDefault(course.getId(), 0L),
                    course.getPublishedAt(),
                    course.getUpdatedAt());
        }).toList();
    }

    /**
     * 详情带章节课时。课时里只给「有没有视频」，不给 videoKey。
     * userId 为 null 表示匿名访问，favorited 恒为 false。
     */
    public CourseDtos.CourseDetailVO detail(Course course, Long userId) {
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

        RatingStat rating = reviewMapper.statByCourse(course.getId());
        boolean favorited = userId != null && favoriteMapper.count(userId, course.getId()) > 0;
        List<TagDtos.TagVO> tags = courseTagMapper.findTagsByCourse(course.getId()).stream()
                .map(tag -> new TagDtos.TagVO(tag.getId(), tag.getName(), tag.getSort()))
                .toList();

        return new CourseDtos.CourseDetailVO(
                course.getId(),
                course.getTeacherId(),
                teacherName(course.getTeacherId()),
                course.getSubjectId(),
                course.getLevel(),
                tags,
                course.getTitle(),
                course.getSummary(),
                course.getDescription(),
                fileStorage.url(course.getCoverKey()),
                course.getStatus(),
                course.getRejectReason(),
                course.getPublishedAt(),
                course.getCreatedAt(),
                // 保留一位小数，避免前端拿到 4.3333333 这种值
                Math.round(rating.avgRating() * 10) / 10.0,
                rating.total(),
                favorited,
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
