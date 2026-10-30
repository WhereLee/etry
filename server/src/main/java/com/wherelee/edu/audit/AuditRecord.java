package com.wherelee.edu.audit;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AuditRecord {

    private Long id;
    private Long userId;
    private String username;
    private String action;
    private String targetType;
    private Long targetId;
    private String detail;
    private String ip;
    private Boolean success;
    private Integer costMs;
    private LocalDateTime createdAt;
}
