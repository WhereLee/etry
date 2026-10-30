package com.wherelee.edu.user.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserProfileVO(
        Long id,
        String username,
        String nickname,
        String bio,
        String avatarUrl,
        List<String> roles,
        LocalDateTime createdAt
) {
}
