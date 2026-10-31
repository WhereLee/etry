package com.wherelee.edu.course;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CourseAnswer {

    private Long id;
    private Long questionId;
    private Long userId;
    private String content;
    private LocalDateTime createdAt;
}
