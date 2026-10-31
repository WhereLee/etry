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

    /** 删除对象，键不存在时静默忽略 */
    void delete(String key);

    /** 对象键转可访问 URL */
    String url(String key);
}
