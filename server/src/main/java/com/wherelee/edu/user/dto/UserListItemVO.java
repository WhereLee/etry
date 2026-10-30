package com.wherelee.edu.user.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserListItemVO(
        Long id,
        String username,
        String nickname,
        String status,
        List<String> roles,
        LocalDateTime createdAt
) {
}
