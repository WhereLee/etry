package com.wherelee.edu.user;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Role {

    public static final String CODE_STUDENT = "STUDENT";
    public static final String CODE_TEACHER = "TEACHER";
    public static final String CODE_ADMIN = "ADMIN";

    private Long id;
    private String code;
    private String name;
    private String description;
    private LocalDateTime createdAt;
}
