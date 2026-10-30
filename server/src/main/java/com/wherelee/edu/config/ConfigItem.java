package com.wherelee.edu.config;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ConfigItem {

    private Long id;
    private String configKey;
    private String configValue;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
