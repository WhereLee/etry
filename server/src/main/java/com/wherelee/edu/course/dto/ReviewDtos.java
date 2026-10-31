package com.wherelee.edu.course.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class ReviewDtos {

    /** 提交评价：可以只打分不写字 */
    public record SubmitRequest(

            @NotNull(message = "请选择评分")
            @Min(value = 1, message = "评分最低 1 星")
            @Max(value = 5, message = "评分最高 5 星")
            Integer rating,

            @Size(max = 500, message = "评价不能超过 500 字")
            String content
    ) {
    }

    public record ReviewVO(
            Long id,
            Long userId,
            String nickname,
            Integer rating,
            String content,
            LocalDateTime createdAt
    ) {
    }

    /** 我的评价：用于详情页回显自己打过的分 */
    public record MyReviewVO(Integer rating, String content, LocalDateTime updatedAt) {
    }
}
