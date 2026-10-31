package com.wherelee.edu.common;

import lombok.Getter;

/**
 * 业务错误码。HTTP 状态码统一 200，由 code 区分结果，前端只处理一套语义。
 * 4xxxx 客户端问题，5xxxx 服务端问题。
 */
@Getter
public enum ErrorCode {

    INVALID_PARAM(40001, "参数不合法"),
    UNAUTHORIZED(40101, "未登录或登录已失效"),
    LOGIN_FAILED(40102, "账号或密码错误"),
    ACCOUNT_DISABLED(40103, "账号已被禁用"),
    LOGIN_LOCKED(40104, "登录失败次数过多，请稍后再试"),
    REGISTER_DISABLED(40105, "当前未开放注册"),
    MUST_CHANGE_PASSWORD(40106, "请先修改初始密码"),
    FORBIDDEN(40301, "无权访问"),
    TEACHER_REQUIRED(40302, "需要先通过讲师认证"),
    NOT_FOUND(40400, "资源不存在"),

    USERNAME_EXISTS(40002, "用户名已被占用"),
    OLD_PASSWORD_WRONG(40003, "原密码不正确"),
    USER_NOT_FOUND(40401, "用户不存在"),

    ROLE_NOT_FOUND(40402, "角色不存在"),
    ROLE_CODE_EXISTS(40004, "角色编码已存在"),
    ROLE_IN_USE(40005, "角色下仍有用户，不能删除"),
    LAST_ADMIN(40012, "系统必须保留至少一个可用的管理员"),

    TEACHER_NOT_FOUND(40403, "讲师档案不存在"),
    TEACHER_PROFILE_EXISTS(40006, "你已经提交过讲师申请"),
    TEACHER_STATUS_INVALID(40007, "当前状态不允许该操作"),

    SUBJECT_NOT_FOUND(40404, "学科不存在"),
    SUBJECT_CODE_EXISTS(40008, "学科编码已存在"),
    SUBJECT_IN_USE(40009, "学科下仍有讲师，不能删除"),

    NOTICE_NOT_FOUND(40405, "公告不存在"),
    NOTICE_STATUS_INVALID(40010, "公告状态不允许该操作"),

    MESSAGE_NOT_FOUND(40406, "消息不存在"),
    MESSAGE_NOT_OWNER(40011, "不能操作他人的消息"),

    CONFIG_NOT_FOUND(40407, "配置项不存在"),

    COURSE_NOT_FOUND(40408, "课程不存在"),
    COURSE_STATUS_INVALID(40013, "课程当前状态不允许该操作"),
    CHAPTER_NOT_FOUND(40409, "章节不存在"),
    LESSON_NOT_FOUND(40410, "课时不存在"),
    QUESTION_NOT_FOUND(40411, "问题不存在"),
    MATERIAL_NOT_FOUND(40412, "课件资料不存在"),
    TAG_NOT_FOUND(40413, "标签不存在"),
    VIDEO_TYPE_INVALID(40014, "只支持 mp4 格式的视频"),
    SEQUENTIAL_REQUIRED(40015, "请先学完上一节"),
    TAG_IN_USE(40016, "标签还有课程在用，不能删除"),
    TAG_NAME_EXISTS(40017, "标签名已存在"),

    INTERNAL_ERROR(50000, "系统繁忙，请稍后重试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
