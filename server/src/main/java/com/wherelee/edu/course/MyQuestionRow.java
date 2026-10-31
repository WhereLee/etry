package com.wherelee.edu.course;

import java.time.LocalDateTime;

/**
 * 我的提问一行。字段顺序必须与 CourseQuestionMapper.findPageByUser 的 SELECT 列顺序一致。
 */
public record MyQuestionRow(
        Long id,
        Long courseId,
        String courseTitle,
        String content,
        Boolean resolved,
        Long answerCount,
        LocalDateTime createdAt
) {
}
