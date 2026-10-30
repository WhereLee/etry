package com.wherelee.etry.user;

import com.wherelee.etry.storage.PresignTicket;

/**
 * 头像上传凭证：objectKey 要原样带回来做确认，服务端才能校验归属。
 */
public record AvatarPresign(String objectKey, PresignTicket ticket) {
}
