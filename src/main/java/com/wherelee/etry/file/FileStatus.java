package com.wherelee.etry.file;

public enum FileStatus {
    /** 已签发凭证，尚未确认上传完成 */
    PENDING,
    /** 可用 */
    READY,
    /** 正在被清理任务处理，用于抢占防止重复执行 */
    PURGING
}
