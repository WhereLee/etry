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
    /** 1=被管理员隐藏，前台不可见（软删，记录保留） */
    private Boolean hidden;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
