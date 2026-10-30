package com.wherelee.edu.config;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.config.dto.ConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final ConfigMapper configMapper;

    public List<ConfigVO> list() {
        return configMapper.findAll().stream().map(ConfigVO::from).toList();
    }

    /**
     * 只允许改已存在的配置项：新增配置项意味着新增业务概念，应该在代码里定义并随迁移落库，
     * 而不是让管理员在后台随意发明 key。
     */
    @Transactional
    public void update(String key, String value) {
        ConfigItem item = configMapper.findByKey(key);
        if (item == null) {
            throw BizException.of(ErrorCode.CONFIG_NOT_FOUND);
        }
        configMapper.updateValue(key, value);
        log.info("配置变更 key={} {} -> {}", key, item.getConfigValue(), value);
    }

    public String get(String key, String defaultValue) {
        ConfigItem item = configMapper.findByKey(key);
        return item == null ? defaultValue : item.getConfigValue();
    }
}
