package com.wherelee.edu.auth.dto;

/**
 * 改密结果。
 *
 * 改密会自增 token_version，当前 token 立刻失效——包括刚才发起这个请求的那个。
 * 所以必须明确告诉前端「去重新登录」，否则开号首改密这条必经路径上，
 * 前端按「改密成功就进工作台」会立刻拿到 40101。
 */
public record PasswordChangeResult(boolean needRelogin) {
}
