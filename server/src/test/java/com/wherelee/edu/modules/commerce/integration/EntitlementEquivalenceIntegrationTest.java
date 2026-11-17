package com.wherelee.edu.modules.commerce.integration;

import com.wherelee.edu.modules.entitlement.api.EntitlementCommand;
import com.wherelee.edu.modules.entitlement.api.EntitlementQuery;
import com.wherelee.edu.modules.entitlement.api.ProductTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权益中心语义集成测试：Phase 3 权威翻转后 t_entitlement 为唯一事实源，
 * 履约/退款/管理员分配的 grant-once/revoke-once/有效期/排序语义钉死。
 */
class EntitlementEquivalenceIntegrationTest extends CommerceIntegrationTestSupport {

    @Autowired private EntitlementCommand entitlementCommand;
    @Autowired private EntitlementQuery entitlementQuery;

    @Test
    void orderFulfillmentGrantsEntitlement() {
        String orderNo = createOrder(nextRequestId()).orderNo();
        completePayment(orderNo);

        assertThat(count("t_entitlement",
                "user_id=? AND product_id=? AND product_type='COURSE_SERIES' AND source='ORDER' AND status='ACTIVE'",
                fx.studentId(), fx.seriesId())).isEqualTo(1);
        assertThat(entitlementQuery.hasActive(fx.studentId(), fx.seriesId(), ProductTypes.COURSE_SERIES)).isTrue();
    }

    @Test
    void fulfillmentIsIdempotent() {
        String orderNo = createOrder(nextRequestId()).orderNo();
        completePayment(orderNo);
        fulfillmentService.fulfill(orderNo);
        fulfillmentService.fulfill(orderNo);

        assertThat(count("t_entitlement",
                "user_id=? AND product_id=? AND product_type='COURSE_SERIES' AND source='ORDER'",
                fx.studentId(), fx.seriesId())).isEqualTo(1);
    }

    @Test
    void refundRevokesEntitlement() {
        String orderNo = createOrder(nextRequestId()).orderNo();
        completePayment(orderNo);
        refundService.refund(orderNo, fx.adminId(), "等价测试退款", false);
        drainOutbox();

        assertThat(count("t_entitlement",
                "user_id=? AND product_id=? AND product_type='COURSE_SERIES' AND source='ORDER' AND status='REVOKED'",
                fx.studentId(), fx.seriesId())).isEqualTo(1);
        assertThat(entitlementQuery.hasActive(fx.studentId(), fx.seriesId(), ProductTypes.COURSE_SERIES)).isFalse();
    }

    @Test
    void adminReplaceManualOrdersEntitlements() {
        entitlementCommand.replaceManual(fx.studentId(), ProductTypes.COURSE_SERIES, List.of(fx.seriesId()));

        assertThat(count("t_entitlement",
                "user_id=? AND product_type='COURSE_SERIES' AND source='ADMIN' AND status='ACTIVE'",
                fx.studentId())).isEqualTo(1);
        assertThat(entitlementQuery.listManualProductIds(fx.studentId(), ProductTypes.COURSE_SERIES))
                .containsExactly(fx.seriesId());

        entitlementCommand.replaceManual(fx.studentId(), ProductTypes.COURSE_SERIES, List.of());
        assertThat(count("t_entitlement",
                "user_id=? AND product_type='COURSE_SERIES' AND source='ADMIN'", fx.studentId())).isZero();
    }

    @Test
    void hasActiveFalseWithoutEntitlement() {
        assertThat(entitlementQuery.hasActive(fx.studentId(), fx.seriesId(), ProductTypes.COURSE_SERIES)).isFalse();
        assertThat(entitlementQuery.listActive(fx.studentId(), ProductTypes.COURSE_SERIES)).isEmpty();
    }
}