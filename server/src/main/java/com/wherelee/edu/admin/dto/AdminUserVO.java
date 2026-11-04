package com.wherelee.edu.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminUserVO {
    private Long id;
    private String username;
    private String nickname;
    private String role;
    private String status;
    private Boolean mustChangePassword;
    private Long seriesCount;
    private LocalDateTime createdAt;
}