package com.wherelee.edu.modules.course.governance;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminSeriesVO {
    private Long id;
    private String title;
    private String summary;
    private String status;
    private String saleStatus;
    private Long priceCents;
    private String currency;
    private Long teacherId;
    private String teacherName;
    private Long chapterCount;
    private Long videoCount;
    private Long assignedStudentCount;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
}
