package com.wherelee.edu.modules.course.governance;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminSeriesVideoVO {
    private Long id;
    private Long chapterId;
    private String title;
    private Integer sort;
    private String mediaStatus;
    private Integer durationSeconds;
    private Long fileSizeBytes;
    private LocalDateTime updatedAt;
}
