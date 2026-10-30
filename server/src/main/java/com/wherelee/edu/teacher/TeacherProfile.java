package com.wherelee.edu.teacher;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class TeacherProfile {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    private Long id;
    private Long userId;
    private String realName;
    private String title;
    private String bio;
    private String avatarKey;
    private Integer years;
    private String status;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
