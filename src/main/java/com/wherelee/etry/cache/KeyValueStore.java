package com.wherelee.etry.cache;

import java.time.Duration;

/**
 * 键值存储抽象。生产用 Redis，本地与测试用内存实现，
 * 业务代码只依赖这个接口，不直接接触 RedisTemplate。
 */
public interface KeyValueStore {

    /** 取值，不存在返回 null */
    String get(String key);

    /** 设置并带过期时间 */
    void setEx(String key, String value, Duration ttl);

    /** 自增，返回自增后的值；键不存在时从 0 开始 */
    long incr(String key);

    /** 给已存在的键设置过期时间 */
    void expire(String key, Duration ttl);

    void delete(String key);

    boolean exists(String key);
}
