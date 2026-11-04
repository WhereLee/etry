package com.wherelee.edu.invitation;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TeacherInvitationVO {
    private Long id;
    private String username;
    private String nickname;
    private String status;
    private LocalDateTime expiresAt;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime usedAt;
    private Boolean expired;
}
