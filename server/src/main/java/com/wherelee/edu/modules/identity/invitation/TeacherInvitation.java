package com.wherelee.edu.modules.identity.invitation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeacherInvitation {
    private Long id;
    private String tokenHash;
    private String username;
    private String nickname;
    private String status;
    private LocalDateTime expiresAt;
    private Long createdBy;
    private Long usedBy;
    private LocalDateTime createdAt;
    private LocalDateTime usedAt;
}
