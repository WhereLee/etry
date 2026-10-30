package com.wherelee.etry.file;

import java.util.UUID;

/**
 * 对象 key 由服务端生成，客户端无法指定，避免覆盖别人的对象。
 * 前缀按用户隔离：u/{userId}/files/... 与 u/{userId}/avatar/...
 */
public final class ObjectKeys {

    private ObjectKeys() {
    }

    public static String file(Long userId, String originalName) {
        return "u/" + userId + "/files/" + UUID.randomUUID() + extension(originalName);
    }

    public static String avatar(Long userId) {
        return "u/" + userId + "/avatar/" + UUID.randomUUID();
    }

    /** 只接受字母数字，最长 10 位，避免奇怪字符破坏 key */
    static String extension(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return "";
        }
        String ext = originalName.substring(dot + 1);
        if (!ext.matches("[A-Za-z0-9]{1,10}")) {
            return "";
        }
        return "." + ext.toLowerCase();
    }
}
