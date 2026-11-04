package com.wherelee.edu.admin;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminSeriesHeaderVO {
    private Long id;
    private String title;
    private String summary;
    private String status;
    private String saleStatus;
    private Long priceCents;
    private String currency;
    private Long teacherId;
    private String teacherName;
    private LocalDateTime updatedAt;
}
