package com.wherelee.edu.modules.entitlement.seed;

import com.wherelee.edu.modules.platform.seed.SeedContext;
import com.wherelee.edu.modules.platform.seed.SeedContributor;
import org.springframework.stereotype.Component;

/**
 * 权益模块种子：把课程贡献者注册的系列分配给演示学生（t_entitlement 归属 entitlement，ADMIN 源）。
 */
@Component
public class EntitlementSeedContributor implements SeedContributor {

    @Override
    public int order() {
        return 30;
    }

    @Override
    public void contribute(SeedContext context) {
        Long studentId = context.userId("student");
        int sort = 1;
        for (Long seriesId : context.seriesIds()) {
            if (studentId == null || seriesId == null) {
                continue;
            }
            context.jdbc().update("""
                    INSERT IGNORE INTO t_entitlement
                        (user_id, product_id, product_type, source, status, display_order, granted_at)
                    VALUES (?, ?, 'COURSE_SERIES', 'ADMIN', 'ACTIVE', ?, CURRENT_TIMESTAMP(3))
                    """, studentId, seriesId, sort++);
        }
    }
}