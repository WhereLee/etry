package com.wherelee.edu.course;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CourseLesson {

    private Long id;
    private Long courseId;
    private Long chapterId;
    private String title;
    /** 视频对象键。为空表示课时还没传视频 */
    private String videoKey;
    private Integer durationSeconds;
    private Long sizeBytes;
    private Integer sort;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
