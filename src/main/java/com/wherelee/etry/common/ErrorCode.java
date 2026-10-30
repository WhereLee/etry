package com.wherelee.etry.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "请求参数不合法"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "请先登录"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "没有权限"),

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "邮箱或密码错误"),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "邮箱尚未验证，请先完成验证"),
    LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "登录失败次数过多，请稍后再试"),
    LOGIN_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "登录请求过于频繁，请稍后再试"),

    CODE_INVALID(HttpStatus.BAD_REQUEST, "验证码错误"),
    CODE_EXPIRED(HttpStatus.BAD_REQUEST, "验证码已失效，请重新获取"),
    RESEND_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "发送过于频繁，请稍后再试"),
    DAILY_LIMIT_REACHED(HttpStatus.TOO_MANY_REQUESTS, "今日邮件额度已满，明日重置"),
    MAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "邮件发送失败，请重试"),

    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "原密码不正确"),

    QUOTA_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "剩余空间不足"),
    FILE_SIZE_INVALID(HttpStatus.BAD_REQUEST, "文件大小超出限制"),
    FILE_TYPE_INVALID(HttpStatus.BAD_REQUEST, "不支持的文件类型"),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "文件不存在"),
    FILE_STATE_INVALID(HttpStatus.CONFLICT, "文件状态已变化，请刷新后重试"),
    OBJECT_NOT_FOUND(HttpStatus.CONFLICT, "对象存储中找不到该文件，请重新上传"),
    OBJECT_SIZE_MISMATCH(HttpStatus.CONFLICT, "文件大小与声明不一致，请重新上传"),
    STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "存储服务异常"),

    SHARE_NOT_FOUND(HttpStatus.NOT_FOUND, "分享不存在或已失效"),
    SHARE_EXPIRED(HttpStatus.GONE, "分享已过期"),
    SHARE_REVOKED(HttpStatus.GONE, "分享已取消"),
    SHARE_CODE_REQUIRED(HttpStatus.BAD_REQUEST, "请输入提取码"),
    SHARE_CODE_INVALID(HttpStatus.BAD_REQUEST, "提取码错误"),
    SHARE_CODE_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "尝试次数过多，请稍后再试"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器开小差了");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
