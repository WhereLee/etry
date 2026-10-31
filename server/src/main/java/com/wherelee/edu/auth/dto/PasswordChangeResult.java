package com.wherelee.edu.auth.dto;

/**
 * 改密结果。
 *
 * 改密会自增 token_version，刚才那个 token 立刻失效。所以这里直接返回一个新 token，
 * 让当前设备无缝接着用；其他设备上的 token 因为版本对不上，下次请求就会被踢下去。
 *
 * needRelogin 保留字段位置，始终为 false，仅用于兼容旧前端：
 * 如果哪天真需要强制重登，把它置 true 并把 token 置空即可。
 */
public record PasswordChangeResult(boolean needRelogin, String token) {
}
