package com.wherelee.edu.audit;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuditMapper {

    @Insert("INSERT INTO t_audit_log (user_id, username, action, target_type, target_id, detail, ip, success, cost_ms) "
            + "VALUES (#{userId}, #{username}, #{action}, #{targetType}, #{targetId}, #{detail}, #{ip}, #{success}, #{costMs})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AuditRecord record);

    /** 分页查询，动态条件在 AuditMapper.xml */
    List<AuditRecord> findPage(@Param("userId") Long userId,
                               @Param("action") String action,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    long countPage(@Param("userId") Long userId, @Param("action") String action);
}
