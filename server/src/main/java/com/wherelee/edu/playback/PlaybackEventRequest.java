package com.wherelee.edu.playback;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlaybackEventRequest(
        @NotNull(message = "视频 ID 不能为空") Long videoId,
        @NotBlank(message = "事件类型不能为空")
        @Pattern(regexp = "PLAY|PAUSE|SEEKING|SEEKED|WAITING|STALLED|ERROR|ENDED|URL_REFRESH|LOADED_METADATA|HIDDEN",
                message = "不支持的事件类型")
        String eventType,
        @Min(value = 0, message = "播放位置不能小于 0")
        @Max(value = 86400, message = "播放位置超出范围")
        Integer positionSeconds,
        @Min(value = 0, message = "视频时长不能小于 0")
        @Max(value = 86400, message = "视频时长超出范围")
        Integer durationSeconds,
        @Size(max = 500, message = "错误信息不能超过 500 字")
        String message,
        @Size(max = 64, message = "会话 ID 不能超过 64 字")
        String sessionId
) {
}