package com.wherelee.etry.user.dto;

import java.time.Instant;
import java.util.Map;

/**
 * 头像上传凭证，字段名与文件直传保持一致，前端可以复用同一个上传函数。
 */
public record AvatarUploadResponse(String objectKey, String uploadUrl, String method,
                                   Map<String, String> formFields, Map<String, String> headers,
                                   Instant expireAt) {
}
