package com.wherelee.etry.file;

import java.util.Map;

/**
 * 按扩展名推导 Content-Type。客户端传来的类型只做参考，
 * 最终以这里的白名单为准，避免被塞进奇怪类型。
 */
public final class ContentTypes {

    private static final String DEFAULT_TYPE = "application/octet-stream";

    private static final Map<String, String> BY_EXTENSION = Map.ofEntries(
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("xml", "application/xml"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("zip", "application/zip"),
            Map.entry("gz", "application/gzip"),
            Map.entry("rar", "application/vnd.rar"),
            Map.entry("7z", "application/x-7z-compressed"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("mov", "video/quicktime"),
            Map.entry("webm", "video/webm"));

    private ContentTypes() {
    }

    public static String resolve(String originalName) {
        String extension = ObjectKeys.extension(originalName);
        if (extension.isEmpty()) {
            return DEFAULT_TYPE;
        }
        return BY_EXTENSION.getOrDefault(extension.substring(1), DEFAULT_TYPE);
    }
}
