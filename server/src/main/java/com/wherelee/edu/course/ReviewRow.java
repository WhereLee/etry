package com.wherelee.edu.course;

import java.time.LocalDateTime;

/** 评价列表一行：评价本身 + 评价人昵称，一次 join 查完 */
public record ReviewRow(
        Long id,
        Long userId,
        String nickname,
        Integer rating,
        String content,
        LocalDateTime createdAt
) {
}
