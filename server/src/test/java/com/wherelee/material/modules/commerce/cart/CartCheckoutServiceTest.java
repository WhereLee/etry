package com.wherelee.material.modules.commerce.cart;

import com.wherelee.material.modules.commerce.api.OrderDtos;
import com.wherelee.material.modules.commerce.api.SellableCatalog;
import com.wherelee.material.modules.commerce.order.OrderItemRecord;
import com.wherelee.material.modules.commerce.order.OrderItemRepository;
import com.wherelee.material.modules.commerce.order.OrderRecord;
import com.wherelee.material.modules.commerce.order.OrderRepository;
import com.wherelee.material.modules.commerce.order.OrderService;
import com.wherelee.material.modules.commerce.order.OrderStatus;
import com.wherelee.material.modules.commerce.payment.PaymentMetrics;
import com.wherelee.material.modules.commerce.payment.PaymentProperties;
import com.wherelee.material.modules.entitlement.api.EntitlementQuery;
import com.wherelee.material.modules.entitlement.api.ProductTypes;
import com.wherelee.material.modules.platform.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartCheckoutServiceTest {

    @Mock private CartRepository cartRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderService orderService;
    @Mock private EntitlementQuery entitlementQuery;
    @Mock private PaymentMetrics paymentMetrics;
    @Mock private SellableCatalog sellableCatalog;

    private CartCheckoutService service;

    @BeforeEach
    void setUp() {
        when(sellableCatalog.productType()).thenReturn(ProductTypes.MATERIAL);
        service = new CartCheckoutService(cartRepository, orderRepository, orderItemRepository,
                orderService, entitlementQuery,
                new PaymentProperties("mock", true, "secret", 15, 15, 20, 15000, 10000, 5, 30),
                paymentMetrics, List.of(sellableCatalog));
    }

    @Test
    void duplicateClientRequestReturnsExistingOrder() {
        OrderRecord existing = cartOrder(OrderStatus.CREATED, LocalDateTime.now().plusMinutes(15));
        when(orderRepository.findByClientRequest(1L, "req-1")).thenReturn(Optional.of(existing));
        when(orderService.view(existing, 1L)).thenReturn(view(existing.orderNo()));

        OrderDtos.OrderView result = service.checkout(1L,
                new CartDtos.CheckoutRequest(List.of(1L, 2L), "req-1"));

        assertThat(result.orderNo()).isEqualTo(existing.orderNo());
        verify(orderRepository, never()).insertCartOrder(anyString(), anyLong(), anyString(),
                anyLong(), anyString(), anyString(), any());
    }

    @Test
    void materialNotInCartIsRejected() {
        when(orderRepository.findByClientRequest(1L, "req-2")).thenReturn(Optional.empty());
        when(cartRepository.findByUser(1L)).thenReturn(List.of(cartItem(1L)));

        assertThatThrownBy(() -> service.checkout(1L,
                new CartDtos.CheckoutRequest(List.of(1L, 2L), "req-2")))
                .isInstanceOf(BizException.class)
                .hasMessage("请先将资料加入购物车");
    }

    @Test
    void activeOrderContainingSelectedMaterialIsReused() {
        OrderRecord active = cartOrder(OrderStatus.CREATED, LocalDateTime.now().plusMinutes(10));
        stubCartAndNoClientRequest("req-3");
        when(orderRepository.findActiveByMaterialIds(1L, List.of(1L, 2L))).thenReturn(Optional.of(active));
        when(orderService.view(active, 1L)).thenReturn(view(active.orderNo()));

        OrderDtos.OrderView result = service.checkout(1L,
                new CartDtos.CheckoutRequest(List.of(1L, 2L), "req-3"));

        assertThat(result.orderNo()).isEqualTo(active.orderNo());
        verify(orderRepository, never()).insertCartOrder(anyString(), anyLong(), anyString(),
                anyLong(), anyString(), anyString(), any());
    }

    @Test
    void expiredActiveOrderIsClosedBeforeNewOrder() {
        OrderRecord expired = cartOrder(OrderStatus.CREATED, LocalDateTime.now().minusMinutes(1));
        stubCartAndNoClientRequest("req-4");
        when(orderRepository.findActiveByMaterialIds(1L, List.of(1L, 2L))).thenReturn(Optional.of(expired));
        stubPurchasable();
        stubInsertSuccess();

        service.checkout(1L, new CartDtos.CheckoutRequest(List.of(1L, 2L), "req-4"));

        verify(orderRepository).close(expired.orderNo(), false);
        verify(orderRepository).insertCartOrder(anyString(), eq(1L), eq("资料订单 共2件"),
                eq(2580L), eq("CNY"), eq("req-4"), any(LocalDateTime.class));
    }

    @Test
    void purchasedMaterialIsRejected() {
        stubCartAndNoClientRequest("req-5");
        when(orderRepository.findActiveByMaterialIds(1L, List.of(1L, 2L))).thenReturn(Optional.empty());
        when(sellableCatalog.findPurchasable(1L)).thenReturn(Optional.of(product(1L, "数学试卷", 990L)));
        when(entitlementQuery.hasActive(1L, 1L, ProductTypes.MATERIAL)).thenReturn(true);

        assertThatThrownBy(() -> service.checkout(1L,
                new CartDtos.CheckoutRequest(List.of(1L, 2L), "req-5")))
                .isInstanceOf(BizException.class)
                .hasMessage("您已购买其中部分资料，请刷新购物车");
        verify(orderRepository, never()).insertCartOrder(anyString(), anyLong(), anyString(),
                anyLong(), anyString(), anyString(), any());
    }

    @Test
    void offSaleMaterialIsRejected() {
        stubCartAndNoClientRequest("req-6");
        when(orderRepository.findActiveByMaterialIds(1L, List.of(1L, 2L))).thenReturn(Optional.empty());
        when(sellableCatalog.findPurchasable(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkout(1L,
                new CartDtos.CheckoutRequest(List.of(1L, 2L), "req-6")))
                .isInstanceOf(BizException.class)
                .hasMessage("资料已下架或不可售，请刷新购物车");
    }

    @Test
    void checkoutCreatesOrderWithSnapshotsAndClearsCart() {
        stubCartAndNoClientRequest("req-7");
        when(orderRepository.findActiveByMaterialIds(1L, List.of(1L, 2L))).thenReturn(Optional.empty());
        stubPurchasable();
        stubInsertSuccess();

        OrderDtos.OrderView result = service.checkout(1L,
                new CartDtos.CheckoutRequest(List.of(1L, 2L, 1L), "req-7"));

        assertThat(result.orderNo()).isEqualTo("ORD-NEW");
        ArgumentCaptor<List<OrderItemRecord>> items = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).insertAll(eq(10L), items.capture());
        assertThat(items.getValue()).hasSize(2);
        assertThat(items.getValue().get(0).titleSnapshot()).isEqualTo("数学试卷");
        assertThat(items.getValue().get(0).priceCents()).isEqualTo(990L);
        assertThat(items.getValue().get(1).materialId()).isEqualTo(2L);
        verify(cartRepository).removeByUserAndMaterials(1L, List.of(1L, 2L));
        verify(paymentMetrics).orderCreated();
    }

    @Test
    void duplicateKeyFallsBackToExistingOrder() {
        OrderRecord existing = cartOrder(OrderStatus.CREATED, LocalDateTime.now().plusMinutes(15));
        stubCartAndNoClientRequest("req-8");
        when(orderRepository.findActiveByMaterialIds(1L, List.of(1L, 2L))).thenReturn(Optional.empty());
        stubPurchasable();
        when(orderRepository.insertCartOrder(anyString(), anyLong(), anyString(), anyLong(),
                anyString(), anyString(), any())).thenThrow(new DuplicateKeyException("dup"));
        when(orderRepository.findByClientRequest(1L, "req-8"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(orderService.view(existing, 1L)).thenReturn(view(existing.orderNo()));

        OrderDtos.OrderView result = service.checkout(1L,
                new CartDtos.CheckoutRequest(List.of(1L, 2L), "req-8"));

        assertThat(result.orderNo()).isEqualTo(existing.orderNo());
        verify(orderItemRepository, never()).insertAll(anyLong(), anyList());
        verify(cartRepository, never()).removeByUserAndMaterials(anyLong(), anyList());
    }

    private void stubCartAndNoClientRequest(String requestId) {
        when(orderRepository.findByClientRequest(1L, requestId)).thenReturn(Optional.empty());
        when(cartRepository.findByUser(1L)).thenReturn(List.of(cartItem(1L), cartItem(2L)));
    }

    private void stubPurchasable() {
        when(sellableCatalog.findPurchasable(1L)).thenReturn(Optional.of(product(1L, "数学试卷", 990L)));
        when(sellableCatalog.findPurchasable(2L)).thenReturn(Optional.of(product(2L, "英语教辅", 1590L)));
    }

    private void stubInsertSuccess() {
        when(orderRepository.insertCartOrder(anyString(), anyLong(), anyString(), anyLong(),
                anyString(), anyString(), any())).thenReturn(10L);
        OrderRecord created = cartOrder(OrderStatus.CREATED, LocalDateTime.now().plusMinutes(15));
        when(orderRepository.findByOrderNo(anyString())).thenReturn(Optional.of(created));
        when(orderService.view(created, 1L)).thenReturn(view("ORD-NEW"));
    }

    private SellableCatalog.SellableProduct product(long id, String title, long price) {
        return new SellableCatalog.SellableProduct(id, ProductTypes.MATERIAL, title, price, "CNY");
    }

    private CartItemRecord cartItem(long materialId) {
        return new CartItemRecord(materialId, 1L, materialId, LocalDateTime.now());
    }

    private OrderRecord cartOrder(OrderStatus status, LocalDateTime expireAt) {
        LocalDateTime now = LocalDateTime.now();
        return new OrderRecord(1L, "ORD-1", 1L, 0L, ProductTypes.MATERIAL, "资料订单 共2件",
                2580L, "CNY", status, null, null, "req", null, expireAt,
                null, null, null, 0, null, now, now);
    }

    private OrderDtos.OrderView view(String orderNo) {
        LocalDateTime now = LocalDateTime.now();
        return new OrderDtos.OrderView(orderNo, 0L, ProductTypes.MATERIAL, "资料订单 共2件",
                0L, "资料订单 共2件", 2580L, "CNY", "CREATED", "待支付", null,
                now.plusMinutes(15), null, null, now, false);
    }
}