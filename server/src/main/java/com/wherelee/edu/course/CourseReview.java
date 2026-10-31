package com.wherelee.edu.course;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CourseReview {

    private Long id;
    private Long courseId;
    private Long userId;
    /** 1-5 星 */
    private Integer rating;
    /** 可以只打分不写字 */
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
