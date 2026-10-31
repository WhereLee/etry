package com.wherelee.edu.course;

import java.time.LocalDateTime;

/** 回答列表一行：回答 + 回答人昵称 */
public record AnswerRow(
        Long id,
        Long userId,
        String nickname,
        String content,
        LocalDateTime createdAt
) {
}
