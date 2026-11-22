package com.wherelee.material.modules.platform.config;

import com.wherelee.material.modules.platform.seed.SeedContext;
import com.wherelee.material.modules.platform.seed.SeedContributor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 示例数据编排器。platform 不直接写业务表：按 order 调度各模块的 SeedContributor，
 * 身份/课程/权益各自贡献自己归属表的种子，跨模块依赖只通过 SeedContext 传递标识。
 */
@Slf4j
@Component
@Profile("!integration")
public class DataInitializer implements ApplicationRunner {

    private final List<SeedContributor> contributors;
    private final JdbcTemplate jdbc;

    public DataInitializer(List<SeedContributor> contributors, JdbcTemplate jdbc) {
        this.contributors = contributors;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        SeedContext context = new SeedContext(jdbc);
        contributors.stream()
                .sorted(Comparator.comparingInt(SeedContributor::order))
                .forEach(contributor -> contributor.contribute(context));
        log.info("学习空间示例数据就绪 student={} series={}", context.userId("student"), context.seriesIds());
    }
}