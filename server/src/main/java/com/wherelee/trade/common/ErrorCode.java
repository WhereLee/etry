package com.wherelee.trade.common;

import lombok.Getter;

/**
 * 业务错误码。HTTP 层统一返回 200，由 code 区分结果，避免前端区分两套语义。
 * 4xxxx 客户端错误，5xxxx 服务端错误。
 */
@Getter
public enum ErrorCode {

    INVALID_PARAM(40001, "参数不合法"),
    UNAUTHORIZED(40101, "未登录或登录已失效"),
    LOGIN_FAILED(40102, "账号或密码错误"),
    FORBIDDEN(40301, "无权访问"),

    USERNAME_EXISTS(40005, "用户名已被占用"),
    OLD_PASSWORD_WRONG(40006, "原密码不正确"),

    PRODUCT_NOT_FOUND(40401, "商品不存在"),
    PRODUCT_NOT_ON_SALE(40002, "商品已下架"),
    STOCK_NOT_ENOUGH(40003, "库存不足"),
    POINT_NOT_ENOUGH(40004, "积分不足"),
    POINT_ACCOUNT_NOT_FOUND(40402, "积分账户不存在"),
    ORDER_NOT_FOUND(40403, "订单不存在"),

    INTERNAL_ERROR(50000, "系统繁忙，请稍后重试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
