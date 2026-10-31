package com.wherelee.edu.course.enrollment;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 学员与课程的关系。
 *
 * 这张表回答的是「我和这门课是什么关系」，不是「我学到哪了」——
 * 学到哪由 t_learning_progress 负责。两者分开的好处是：
 * 只看不学的课能留痕，学了一半放弃的课能保留进度。
 */
@Getter
@Setter
public class CourseEnrollment {

    /** 已加入，还没开始播 */
    public static final String STATUS_JOINED = "JOINED";
    /** 在学 */
    public static final String STATUS_LEARNING = "LEARNING";
    /** 所有有视频的课时都学完了 */
    public static final String STATUS_COMPLETED = "COMPLETED";
    /** 用户主动移出，记录保留，进度不清 */
    public static final String STATUS_QUIT = "QUIT";

    private Long id;
    private Long userId;
    private Long courseId;
    private String status;
    /** 上次看到的课时数，用来感知讲师增删课时 */
    private Integer lessonCountSnapshot;
    private LocalDateTime joinedAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
