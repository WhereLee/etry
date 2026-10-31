package com.wherelee.edu.course;

import java.time.LocalDateTime;

/** 问题列表一行：问题 + 提问人昵称 + 回答数（子查询一次带出，避免逐行再查） */
public record QuestionRow(
        Long id,
        Long userId,
        String nickname,
        String content,
        Boolean resolved,
        long answerCount,
        LocalDateTime createdAt
) {
}
