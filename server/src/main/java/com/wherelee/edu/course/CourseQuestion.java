package com.wherelee.edu.course;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CourseQuestion {

    private Long id;
    private Long courseId;
    private Long userId;
    private String content;
    private Boolean resolved;
    private LocalDateTime createdAt;
}
