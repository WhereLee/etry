package com.wherelee.material.modules.platform.seed;

/**
 * 种子贡献者端口。各业务模块实现本端口贡献自己归属表的示例数据，
 * platform 的 DataInitializer 仅按 order 编排，不直接写业务表。
 */
public interface SeedContributor {

    int order();

    void contribute(SeedContext context);
}