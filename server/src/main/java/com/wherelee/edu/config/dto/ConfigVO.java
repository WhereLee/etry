package com.wherelee.edu.config.dto;

import com.wherelee.edu.config.ConfigItem;

public record ConfigVO(String key, String value, String remark) {

    public static ConfigVO from(ConfigItem item) {
        return new ConfigVO(item.getConfigKey(), item.getConfigValue(), item.getRemark());
    }
}
