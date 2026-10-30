package com.wherelee.edu.storage;

/**
 * 文件存储抽象。当前只有本地实现，将来接对象存储时新增一个实现并在配置里切换，
 * 上层业务代码不用动。
 *
 * 只存「对象键」，对外 URL 由实现拼装：本地是 /files/xxx，对象存储是签名 URL。
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

    /** 删除对象，键不存在时静默忽略 */
    void delete(String key);

    /** 对象键转可访问 URL */
    String url(String key);
}
