package com.wherelee.edu.course;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CourseChapter {

    private Long id;
    private Long courseId;
    private String title;
    private Integer sort;
    private LocalDateTime createdAt;
}
