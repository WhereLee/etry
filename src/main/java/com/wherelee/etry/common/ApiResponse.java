package com.wherelee.etry.common;

/**
 * 统一响应体。接口一律返回这个结构，页面请求不经过它。
 */
public record ApiResponse<T>(boolean success, String code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, "OK", "ok", null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.name(), errorCode.getDefaultMessage(), null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.name(), message, null);
    }
}
