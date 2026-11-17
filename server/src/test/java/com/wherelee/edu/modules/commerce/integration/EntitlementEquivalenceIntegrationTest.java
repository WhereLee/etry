package com.wherelee.edu.modules.commerce.integration;

import com.wherelee.edu.modules.entitlement.api.EntitlementCommand;
import com.wherelee.edu.modules.entitlement.api.EntitlementQuery;
import com.wherelee.edu.modules.entitlement.api.ProductTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权益中心等價集成测试：Phase 1 双写下，履约/退款/管理员分配在 t_student_series（权威）
 * 与 t_entitlement（影子）两表语义一致，端口读行为与旧表一致。
 */
class EntitlementEquivalenceIntegrationTest extends CommerceIntegrationTestSupport {

    @Autowired private EntitlementCommand entitlementCommand;
    @Autowired private EntitlementQuery entitlementQuery;

    @Test
    void orderFulfillmentDualWritesBothTables() {
        String orderNo = createOrder(nextRequestId()).orderNo();
        completePayment(orderNo);

        assertThat(count("t_student_series",
                "user_id=? AND series_id=? AND source='ORDER' AND status='ACTIVE'",
                fx.studentId(), fx.seriesId())).isEqualTo(1);
        assertThat(count("t_entitlement",
                "user_id=? AND product_id=? AND product_type='COURSE_SERIES' AND source='ORDER' AND status='ACTIVE'",
                fx.studentId(), fx.seriesId())).isEqualTo(1);
        assertThat(entitlementQuery.hasActive(fx.studentId(), fx.seriesId(), ProductTypes.COURSE_SERIES)).isTrue();
    }

    @Test
    void fulfillmentIsIdempotentAcrossBothTables() {
        String orderNo = createOrder(nextRequestId()).orderNo();
        completePayment(orderNo);
        fulfillmentService.fulfill(orderNo);
        fulfillmentService.fulfill(orderNo);

        assertThat(count("t_student_series",
                "user_id=? AND series_id=? AND source='ORDER'", fx.studentId(), fx.seriesId())).isEqualTo(1);
        assertThat(count("t_entitlement",
                "user_id=? AND product_id=? AND product_type='COURSE_SERIES' AND source='ORDER'",
                fx.studentId(), fx.seriesId())).isEqualTo(1);
    }

    @Test
    void refundRevokesBothTables() {
        String orderNo = createOrder(nextRequestId()).orderNo();
        completePayment(orderNo);
        refundService.refund(orderNo, fx.adminId(), "等价测试退款", false);
        drainOutbox();

        assertThat(count("t_student_series",
                "user_id=? AND series_id=? AND source='ORDER' AND status='REVOKED'",
                fx.studentId(), fx.seriesId())).isEqualTo(1);
        assertThat(count("t_entitlement",
                "user_id=? AND product_id=? AND product_type='COURSE_SERIES' AND source='ORDER' AND status='REVOKED'",
                fx.studentId(), fx.seriesId())).isEqualTo(1);
        assertThat(entitlementQuery.hasActive(fx.studentId(), fx.seriesId(), ProductTypes.COURSE_SERIES)).isFalse();
    }

    @Test
    void adminReplaceManualDualWritesAndOrders() {
        entitlementCommand.replaceManual(fx.studentId(), ProductTypes.COURSE_SERIES, List.of(fx.seriesId()));

        assertThat(count("t_student_series",
                "user_id=? AND source='ADMIN' AND status='ACTIVE'", fx.studentId())).isEqualTo(1);
        assertThat(count("t_entitlement",
                "user_id=? AND product_type='COURSE_SERIES' AND source='ADMIN' AND status='ACTIVE'",
                fx.studentId())).isEqualTo(1);
        assertThat(entitlementQuery.listManualProductIds(fx.studentId(), ProductTypes.COURSE_SERIES))
                .containsExactly(fx.seriesId());

        entitlementCommand.replaceManual(fx.studentId(), ProductTypes.COURSE_SERIES, List.of());
        assertThat(count("t_student_series",
                "user_id=? AND source='ADMIN'", fx.studentId())).isZero();
        assertThat(count("t_entitlement",
                "user_id=? AND product_type='COURSE_SERIES' AND source='ADMIN'", fx.studentId())).isZero();
    }

    @Test
    void hasActiveFalseWithoutEntitlement() {
        assertThat(entitlementQuery.hasActive(fx.studentId(), fx.seriesId(), ProductTypes.COURSE_SERIES)).isFalse();
        assertThat(entitlementQuery.listActive(fx.studentId(), ProductTypes.COURSE_SERIES)).isEmpty();
    }
}