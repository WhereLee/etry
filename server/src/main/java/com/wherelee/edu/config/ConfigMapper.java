package com.wherelee.edu.config;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ConfigMapper {

    @Select("SELECT id, config_key, config_value, remark, created_at, updated_at FROM t_config ORDER BY config_key")
    List<ConfigItem> findAll();

    @Select("SELECT id, config_key, config_value, remark, created_at, updated_at FROM t_config WHERE config_key = #{key}")
    ConfigItem findByKey(@Param("key") String key);

    @Update("UPDATE t_config SET config_value = #{value} WHERE config_key = #{key}")
    int updateValue(@Param("key") String key, @Param("value") String value);
}
