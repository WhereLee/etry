package com.wherelee.edu.modules.identity.api;

/**
 * 管理员账号写操作契约。
 */
public interface UserAccountOperations {

    Long createStudent(String username, String rawPassword, String nickname);

    void changeStatus(Long operatorId, Long userId, String status);

    void resetPassword(Long operatorId, Long userId, String rawPassword);

    UserAccount require(Long userId);
}