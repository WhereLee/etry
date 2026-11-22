package com.wherelee.material.modules.identity.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理台账号读端口：分页与计数只查 identity 归属的用户表，
 * 关键字命中用户名/昵称，role/status 精确过滤。跨模块统计由消费方经各自端口叠加。
 */
public interface UserAccountQuery {

    List<UserRow> page(String keyword, String role, String status, int offset, int size);

    long count(String keyword, String role, String status);

    record UserRow(Long id, String username, String nickname, String role, String status,
                   boolean mustChangePassword, LocalDateTime createdAt) {
    }
}