package com.wherelee.edu.learning;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public final class LearningProgressDtos {

    public record UpdateRequest(
            @Min(value = 0, message = "播放位置不能小于 0")
            @Max(value = 86400, message = "播放位置超出范围")
            int positionSeconds,
            @Min(value = 0, message = "视频时长不能小于 0")
            @Max(value = 86400, message = "视频时长超出范围")
            int durationSeconds,
            boolean completed
    ) {
    }

    private LearningProgressDtos() {
    }
}
