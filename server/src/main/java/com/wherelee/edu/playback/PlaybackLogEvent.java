package com.wherelee.edu.playback;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaybackLogEvent {
    private Long userId;
    private Long videoId;
    private Long seriesId;
    private String source;
    private String eventType;
    private Integer positionSeconds;
    private Integer durationSeconds;
    private Boolean cacheHit;
    private String message;
    private String userAgent;
    private String sessionId;
    private String traceId;
}
