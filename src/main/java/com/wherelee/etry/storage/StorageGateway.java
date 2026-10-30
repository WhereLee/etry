package com.wherelee.etry.storage;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * 对象存储网关。业务只认这个接口，生产用 COS，本地开发用磁盘实现。
 */
public interface StorageGateway {

    /**
     * 生成浏览器直传凭证。
     *
     * @param objectKey   由服务端生成的精确 key
     * @param sizeBytes   声明大小，用于限制上传体积
     * @param contentType 精确的内容类型
     * @param ttl         凭证有效期
     */
    PresignTicket presignUpload(String objectKey, long sizeBytes, String contentType, Duration ttl);

    /**
     * 生成下载链接。
     *
     * @param downloadName 浏览器保存时使用的文件名
     * @param contentType  对象真实类型；为 null 时退回 application/octet-stream。
     *                     回写真实类型可以避免浏览器把下载当成可疑文件拦下来。
     */
    String presignDownload(String objectKey, String downloadName, String contentType, Duration ttl);

    /** 查询对象元数据，不存在返回 empty */
    Optional<ObjectStat> stat(String objectKey);

    /** 删除对象，对象不存在时静默返回 */
    void delete(String objectKey);
}
