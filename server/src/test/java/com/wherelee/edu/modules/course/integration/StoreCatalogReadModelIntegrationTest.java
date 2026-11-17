package com.wherelee.edu.modules.course.integration;

import com.wherelee.edu.integration.IntegrationTestSupport;
import com.wherelee.edu.modules.commerce.api.OrderDtos;
import com.wherelee.edu.modules.course.catalog.CatalogItem;
import com.wherelee.edu.modules.course.catalog.StoreCatalogReadModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商店目录读模型门面集成测试：课程展示字段 + 端口叠加 owned/activeOrder。
 * 复用模块外共享集成基建的夹具与支付助手。
 */
class StoreCatalogReadModelIntegrationTest extends IntegrationTestSupport {

    @Autowired private StoreCatalogReadModel storeCatalog;

    @Test
    void catalogOverlaysOwnedAndActiveOrderFromReadModel() {
        OrderDtos.OrderView order = createOrder(nextRequestId());

        CatalogItem before = storeCatalog.listForStudent(fx.studentId()).stream()
                .filter(item -> item.seriesId().equals(fx.seriesId())).findFirst().orElseThrow();
        assertThat(before.title()).isEqualTo("IT 系列");
        assertThat(before.teacherName()).isEqualTo("集成测试老师");
        assertThat(before.videoCount()).isEqualTo(1);
        assertThat(before.owned()).isFalse();
        assertThat(before.activeOrderNo()).isEqualTo(order.orderNo());

        completePayment(order.orderNo());

        CatalogItem after = storeCatalog.listForStudent(fx.studentId()).stream()
                .filter(item -> item.seriesId().equals(fx.seriesId())).findFirst().orElseThrow();
        assertThat(after.owned()).isTrue();
        assertThat(after.activeOrderNo()).isNull();
    }
}