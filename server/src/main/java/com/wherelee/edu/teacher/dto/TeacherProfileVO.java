package com.wherelee.edu.teacher.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TeacherProfileVO(
        Long id,
        Long userId,
        String username,
        String realName,
        String title,
        String bio,
        String avatarKey,
        Integer years,
        String status,
        String rejectReason,
        List<SubjectVO> subjects,
        LocalDateTime createdAt
) {
}
