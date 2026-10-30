package com.wherelee.edu.teacher.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TeacherReviewRequest(

        @NotNull(message = "必须明确通过或驳回")
        Boolean approved,

        @Size(max = 200, message = "驳回原因不能超过 200 字")
        String rejectReason
) {
}
