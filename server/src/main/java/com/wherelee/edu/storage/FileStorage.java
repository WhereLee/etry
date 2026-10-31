package com.wherelee.edu.storage;

import java.time.Duration;

/**
 * 文件存储抽象。只存「对象键」，对外 URL 由实现拼装：
 * 本地是 /files/xxx，对象存储是预签名 URL。
 */
public interface FileStorage {

    /**
     * 保存文件，返回对象键（如 avatar/3f2a....png）。
     *
     * @param content   文件字节
     * @param extension 扩展名，由调用方根据内容类型推断，不信任原始文件名
     * @param directory 逻辑目录，如 avatar
     */
    String store(byte[] content, String extension, String directory);

    /**
     * 只生成对象键，不写文件。
     * 用于前端直传：先发签名，传完再由调用方把 key 写进业务表。
     */
    String newKey(String directory, String extension);

    /**
     * 预签名 PUT 地址，前端直接上传到对象存储，字节不经过服务器。
     *
     * Content-Type 必须绑进签名：上传时带的头与这里不一致会 403。
     */
    String presignedPutUrl(String key, String contentType, Duration ttl);

    /**
     * 判断对象键是不是本服务在指定目录下生成的。
     *
     * 用途：老师建课时会提交 videoKey，如果不校验归属，
     * A 只要拿到或猜到 B 目录下的对象键，就能把它写进自己的课时。
     *
     * @param directory 逻辑目录，如 video/12
     */
    boolean ownsKey(String key, String directory);

    /** 读对象的一段字节，用于解析媒体头部（避免把整个视频拉下来） */
    byte[] readRange(String key, long start, long length);

    /** 对象大小（字节） */
    long size(String key);

    /** 删除对象，键不存在时静默忽略 */
    void delete(String key);

    /** 对象键转可访问 URL */
    String url(String key);
}
