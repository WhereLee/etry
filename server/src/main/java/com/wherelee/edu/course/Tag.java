package com.wherelee.edu.course;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Tag {

    private Long id;
    private String name;
    private Integer sort;
    private LocalDateTime createdAt;
}
