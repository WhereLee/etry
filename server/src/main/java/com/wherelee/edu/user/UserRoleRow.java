package com.wherelee.edu.user;

/**
 * 用户与角色的扁平结果，用于列表页一次性把所有人的角色捞出来，避免 N+1 查询。
 */
public record UserRoleRow(Long userId, String roleCode) {
}
