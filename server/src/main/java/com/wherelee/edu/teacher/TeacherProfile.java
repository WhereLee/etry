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
    /** 已归档：讲师离职，档案保留（审计还要对得上人），但不再对外展示、不能再建课 */
    public static final String STATUS_ARCHIVED = "ARCHIVED";

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
