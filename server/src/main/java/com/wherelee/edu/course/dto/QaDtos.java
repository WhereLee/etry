package com.wherelee.edu.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class QaDtos {

    public record AskRequest(

            @NotBlank(message = "问题不能为空")
            @Size(max = 1000, message = "问题不能超过 1000 字")
            String content
    ) {
    }

    public record AnswerRequest(

            @NotBlank(message = "回答不能为空")
            @Size(max = 1000, message = "回答不能超过 1000 字")
            String content
    ) {
    }

    /** 列表项：不带回答正文，只给条数 */
    public record QuestionVO(
            Long id,
            Long userId,
            String nickname,
            String content,
            boolean resolved,
            long answerCount,
            LocalDateTime createdAt
    ) {
    }

    public record AnswerVO(
            Long id,
            Long userId,
            String nickname,
            String content,
            LocalDateTime createdAt
    ) {
    }

    /** 详情：问题 + 全部回答 */
    public record QuestionDetailVO(
            Long id,
            Long courseId,
            Long userId,
            String nickname,
            String content,
            boolean resolved,
            LocalDateTime createdAt,
            List<AnswerVO> answers
    ) {
    }
}
