package com.wherelee.edu.common;

/**
 * 模糊搜索关键词处理。
 *
 * LIKE 里的 % 和 _ 是通配符，用户输一个 % 就会变成全表扫描。
 * 这里统一转义，配合 XML 里的 ESCAPE '\\' 生效。
 */
public final class Keywords {

    private Keywords() {
    }

    public static String escapeLike(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return keyword;
        }
        return keyword.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
