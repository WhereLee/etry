package com.wherelee.edu.teacher.dto;

import java.time.LocalDateTime;

import java.util.List;

/**
 * 对外可见的讲师资料。
 *
 * 和 TeacherProfileVO 的区别：不带 userId、username。
 * 后者是登录名，公开出去等于给攻击者一份准确的爆破名单——
 * 登录接口做的「账号不存在与密码错误统一提示」会被这里绕开。
 */
public record PublicTeacherVO(
        Long id,
        String realName,
        String title,
        String bio,
        String avatarUrl,
        Integer years,
        List<SubjectVO> subjects,
        LocalDateTime createdAt
) {
}
