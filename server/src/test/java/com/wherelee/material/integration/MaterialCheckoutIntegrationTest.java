package com.wherelee.material.integration;

import com.wherelee.material.modules.commerce.api.OrderDtos;
import com.wherelee.material.modules.commerce.cart.CartCheckoutService;
import com.wherelee.material.modules.commerce.cart.CartDtos;
import com.wherelee.material.modules.commerce.cart.CartRepository;
import com.wherelee.material.modules.commerce.order.OrderStatus;
import com.wherelee.material.modules.entitlement.api.EntitlementCommand;
import com.wherelee.material.modules.entitlement.api.ProductTypes;
import com.wherelee.material.modules.material.MaterialRecord;
import com.wherelee.material.modules.material.MaterialRepository;
import com.wherelee.material.modules.platform.common.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 阶段4 交易闭环验证：购物车结算 → 多商品订单快照 → 支付 → 逐行履约 → 整单退款逐行回收。
 * 幂等（client_request_id）、进行中订单复用、并发结算、已购/下架/未加购拦截。
 */
class MaterialCheckoutIntegrationTest extends IntegrationTestSupport {

    @Autowired private CartCheckoutService cartCheckoutService;
    @Autowired private CartRepository cartRepository;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private EntitlementCommand entitlementCommand;

    private long insertMaterial(String title, long priceCents, String saleStatus) {
        return materialRepository.insert(new MaterialRecord(
                null, title, "EXAM_PAPER", "SENIOR", "数学", "交易闭环集成资料",
                priceCents, "CNY", 10, 3, null, "material/checkout.pdf", saleStatus,
                null, null, null));
    }

    private OrderDtos.OrderView checkout(List<Long> materialIds, String requestId) {
        return cartCheckoutService.checkout(fx.studentId(), new CartDtos.CheckoutRequest(materialIds, requestId));
    }

    @Test
    void checkoutCreatesCartOrderWithSnapshotsAndClearsCart() {
        long m1 = insertMaterial("结算试卷A", 990, MaterialRecord.ON_SALE);
        long m2 = insertMaterial("结算教辅B", 1590, MaterialRecord.ON_SALE);
        cartRepository.add(fx.studentId(), m1);
        cartRepository.add(fx.studentId(), m2);

        OrderDtos.OrderView order = checkout(List.of(m1, m2), nextRequestId());

        assertThat(order.productType()).isEqualTo(ProductTypes.MATERIAL);
        assertThat(order.productTitle()).isEqualTo("资料订单 共2件");
        assertThat(order.priceCents()).isEqualTo(2580);
        assertThat(order.status()).isEqualTo("CREATED");
        assertThat(order.owned()).isFalse();

        var record = orderRepository.findByOrderNo(order.orderNo()).orElseThrow();
        assertThat(record.productId()).isZero();
        assertThat(record.activeKey()).isNull();
        var items = orderService.detail(fx.studentId(), order.orderNo()).items();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("结算试卷A");
        assertThat(items.get(0).priceCents()).isEqualTo(990);
        assertThat(items.get(1).materialId()).isEqualTo(m2);
        assertThat(cartRepository.findByUser(fx.studentId())).isEmpty();
    }

    @Test
    void checkoutIsIdempotentByClientRequestId() {
        long m1 = insertMaterial("幂等资料", 990, MaterialRecord.ON_SALE);
        cartRepository.add(fx.studentId(), m1);
        String requestId = nextRequestId();

        OrderDtos.OrderView first = checkout(List.of(m1), requestId);
        OrderDtos.OrderView second = checkout(List.of(m1), requestId);

        assertThat(second.orderNo()).isEqualTo(first.orderNo());
        assertThat(count("t_order", null)).isEqualTo(1);
        assertThat(count("t_order_item", null)).isEqualTo(1);
    }

    @Test
    void concurrentCheckoutWithSameRequestCreatesOneOrder() throws Exception {
        long m1 = insertMaterial("并发资料A", 990, MaterialRecord.ON_SALE);
        long m2 = insertMaterial("并发资料B", 590, MaterialRecord.ON_SALE);
        cartRepository.add(fx.studentId(), m1);
        cartRepository.add(fx.studentId(), m2);
        String requestId = nextRequestId();

        var results = runConcurrently(8, () -> checkout(List.of(m1, m2), requestId));

        assertThat(results).allSatisfy(result -> assertThat(result.error()).isNull());
        assertThat(results.stream().map(result -> result.value().orderNo()).distinct().toList()).hasSize(1);
        assertThat(count("t_order", null)).isEqualTo(1);
        assertThat(count("t_order_item", null)).isEqualTo(2);
        assertThat(cartRepository.findByUser(fx.studentId())).isEmpty();
    }

    @Test
    void activeCartOrderIsReusedAcrossDifferentRequests() {
        long m1 = insertMaterial("复用资料", 990, MaterialRecord.ON_SALE);
        cartRepository.add(fx.studentId(), m1);

        OrderDtos.OrderView first = checkout(List.of(m1), nextRequestId());
        cartRepository.add(fx.studentId(), m1);
        OrderDtos.OrderView second = checkout(List.of(m1), nextRequestId());

        assertThat(second.orderNo()).isEqualTo(first.orderNo());
        assertThat(count("t_order", null)).isEqualTo(1);
    }

    @Test
    void purchasedMaterialBlocksCheckout() {
        long m1 = insertMaterial("已购拦截", 990, MaterialRecord.ON_SALE);
        cartRepository.add(fx.studentId(), m1);
        entitlementCommand.grantManual(new EntitlementCommand.GrantCommand(
                fx.studentId(), m1, ProductTypes.MATERIAL, null, null, null, 1));

        assertThatThrownBy(() -> checkout(List.of(m1), nextRequestId()))
                .isInstanceOf(BizException.class)
                .hasMessage("您已购买其中部分资料，请刷新购物车");
        assertThat(count("t_order", null)).isZero();
    }

    @Test
    void offSaleMaterialBlocksCheckout() {
        long m1 = insertMaterial("下架拦截", 990, MaterialRecord.OFF_SALE);
        cartRepository.add(fx.studentId(), m1);

        assertThatThrownBy(() -> checkout(List.of(m1), nextRequestId()))
                .isInstanceOf(BizException.class)
                .hasMessage("资料已下架或不可售，请刷新购物车");
    }

    @Test
    void materialNotInCartBlocksCheckout() {
        long m1 = insertMaterial("未加购拦截", 990, MaterialRecord.ON_SALE);

        assertThatThrownBy(() -> checkout(List.of(m1), nextRequestId()))
                .isInstanceOf(BizException.class)
                .hasMessage("请先将资料加入购物车");
    }

    @Test
    void multiItemPaymentFulfillsEveryItem() {
        long m1 = insertMaterial("履约资料A", 990, MaterialRecord.ON_SALE);
        long m2 = insertMaterial("履约资料B", 1590, MaterialRecord.ON_SALE);
        cartRepository.add(fx.studentId(), m1);
        cartRepository.add(fx.studentId(), m2);
        OrderDtos.OrderView order = checkout(List.of(m1, m2), nextRequestId());

        completePayment(order.orderNo());

        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.FULFILLED);
        assertThat(entitlementRepository.hasActive(fx.studentId(), m1, ProductTypes.MATERIAL)).isTrue();
        assertThat(entitlementRepository.hasActive(fx.studentId(), m2, ProductTypes.MATERIAL)).isTrue();
        assertThat(count("t_entitlement", "product_type = 'MATERIAL'")).isEqualTo(2);
        var detail = orderService.detail(fx.studentId(), order.orderNo());
        assertThat(detail.order().owned()).isTrue();
        assertThat(detail.timeline()).anySatisfy(item ->
                assertThat(item.title()).isEqualTo("资料权益已发放"));
    }

    @Test
    void wholeOrderRefundRevokesEveryItem() {
        long m1 = insertMaterial("退款资料A", 990, MaterialRecord.ON_SALE);
        long m2 = insertMaterial("退款资料B", 1590, MaterialRecord.ON_SALE);
        cartRepository.add(fx.studentId(), m1);
        cartRepository.add(fx.studentId(), m2);
        OrderDtos.OrderView order = checkout(List.of(m1, m2), nextRequestId());
        completePayment(order.orderNo());

        refundService.refund(order.orderNo(), fx.adminId(), "集成测试整单退款", false);
        drainOutbox();

        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.REFUNDED);
        assertThat(entitlementRepository.hasActive(fx.studentId(), m1, ProductTypes.MATERIAL)).isFalse();
        assertThat(entitlementRepository.hasActive(fx.studentId(), m2, ProductTypes.MATERIAL)).isFalse();
        assertThat(orderService.detail(fx.studentId(), order.orderNo()).order().owned()).isFalse();
    }

    @Test
    void payBlockedWhenAnyItemAlreadyPurchased() {
        long m1 = insertMaterial("支付拦截A", 990, MaterialRecord.ON_SALE);
        long m2 = insertMaterial("支付拦截B", 1590, MaterialRecord.ON_SALE);
        cartRepository.add(fx.studentId(), m1);
        cartRepository.add(fx.studentId(), m2);
        OrderDtos.OrderView order = checkout(List.of(m1, m2), nextRequestId());
        entitlementCommand.grantManual(new EntitlementCommand.GrantCommand(
                fx.studentId(), m1, ProductTypes.MATERIAL, null, null, null, 1));

        assertThatThrownBy(() -> pay(order.orderNo()))
                .isInstanceOf(BizException.class)
                .hasMessage("订单内资料已购买，请重新下单");
    }
}