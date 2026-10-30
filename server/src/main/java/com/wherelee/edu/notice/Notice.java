package com.wherelee.edu.notice;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Notice {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_OFFLINE = "OFFLINE";

    private Long id;
    private String title;
    private String content;
    private Long publisherId;
    private String status;
    private Boolean isTop;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
