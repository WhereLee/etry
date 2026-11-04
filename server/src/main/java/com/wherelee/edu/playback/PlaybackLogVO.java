package com.wherelee.edu.playback;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlaybackLogVO {
    private Long id;
    private String source;
    private String eventType;
    private Long videoId;
    private Long seriesId;
    private Integer positionSeconds;
    private Integer durationSeconds;
    private Boolean cacheHit;
    private String message;
    private String sessionId;
    private String traceId;
    private LocalDateTime createdAt;
}
