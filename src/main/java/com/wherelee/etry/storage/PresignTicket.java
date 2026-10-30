package com.wherelee.etry.storage;

import java.time.Instant;
import java.util.Map;

/**
 * 直传凭证。
 * method=PUT 时：浏览器直接把文件体 PUT 到 url，headers 里的头必须原样带上。
 * method=POST 时：浏览器把 formFields 拼成表单再 POST，文件字段名为 file。
 */
public record PresignTicket(String url, String method,
                            Map<String, String> formFields, Map<String, String> headers,
                            Instant expireAt) {

    public static PresignTicket put(String url, Map<String, String> headers, Instant expireAt) {
        return new PresignTicket(url, "PUT", Map.of(), headers, expireAt);
    }

    public static PresignTicket postForm(String url, Map<String, String> formFields, Instant expireAt) {
        return new PresignTicket(url, "POST", formFields, Map.of(), expireAt);
    }
}
