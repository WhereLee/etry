package com.wherelee.edu.teacher;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Subject {

    private Long id;
    private String code;
    private String name;
    private Integer sort;
    private LocalDateTime createdAt;
}
