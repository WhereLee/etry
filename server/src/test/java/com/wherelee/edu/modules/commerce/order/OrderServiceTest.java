package com.wherelee.edu.modules.commerce.order;

import com.wherelee.edu.modules.platform.common.BizException;
import com.wherelee.edu.modules.commerce.entitlement.EntitlementRepository;
import com.wherelee.edu.modules.commerce.order.dto.OrderDtos;
import com.wherelee.edu.modules.commerce.payment.PaymentProperties;
import com.wherelee.edu.modules.commerce.payment.PaymentRepository;
import com.wherelee.edu.modules.commerce.payment.PaymentMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private CatalogRepository catalogRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private EntitlementRepository entitlementRepository;
    @Mock private PaymentMetrics paymentMetrics;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(catalogRepository, orderRepository, paymentRepository,
                entitlementRepository, new PaymentProperties("mock", true, "secret", 15, 15, 20, 15000, 10000, 5, 30),
                paymentMetrics);
    }

    @Test
    void duplicateClientRequestReturnsExistingOrder() {
        OrderRecord existing = order(OrderStatus.CREATED, 19900L);
        when(orderRepository.findByClientRequest(1L, "req-1")).thenReturn(Optional.of(existing));

        OrderDtos.OrderView view = service.create(1L,
                new OrderDtos.CreateOrderRequest(1L, "req-1"));

        assertThat(view.orderNo()).isEqualTo(existing.orderNo());
        verify(orderRepository, never()).insert(anyString(), anyLong(), anyLong(), anyString(),
                anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void cannotPurchaseAlreadyEntitledCourse() {
        when(entitlementRepository.hasActive(1L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, new OrderDtos.CreateOrderRequest(1L, "req-2")))
                .isInstanceOf(BizException.class)
                .hasMessage("你已经拥有这个课程");

        verify(orderRepository, never()).insert(anyString(), anyLong(), anyLong(), anyString(),
                anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void activeOrderIsReused() {
        OrderRecord active = order(OrderStatus.PAY_FAILED, 19900L);
        when(orderRepository.findByClientRequest(1L, "req-3")).thenReturn(Optional.empty());
        when(entitlementRepository.hasActive(1L, 1L)).thenReturn(false);
        when(orderRepository.findActive(1L, 1L)).thenReturn(Optional.of(active));

        OrderDtos.OrderView view = service.create(1L, new OrderDtos.CreateOrderRequest(1L, "req-3"));

        assertThat(view.orderNo()).isEqualTo(active.orderNo());
        verify(orderRepository, never()).insert(anyString(), anyLong(), anyLong(), anyString(),
                anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void newOrderUsesServerPriceSnapshot() {
        when(orderRepository.findByClientRequest(1L, "req-4")).thenReturn(Optional.empty());
        when(entitlementRepository.hasActive(1L, 1L)).thenReturn(false);
        when(orderRepository.findActive(1L, 1L)).thenReturn(Optional.empty());
        when(catalogRepository.findPurchasable(1L)).thenReturn(Optional.of(new SaleSeries(1L, "物理必修一", 25900L, "CNY")));
        when(orderRepository.findByOrderNo(anyString())).thenReturn(Optional.of(order(OrderStatus.CREATED, 25900L)));

        service.create(1L, new OrderDtos.CreateOrderRequest(1L, "req-4"));

        verify(orderRepository).insert(anyString(), eq(1L), eq(1L), eq("物理必修一"),
                eq(25900L), eq("CNY"), eq("req-4"), anyString(), any(LocalDateTime.class));
    }

    @Test
    void payingOrderCannotBeClosedDirectly() {
        when(orderRepository.findOwned("ORD-1", 1L)).thenReturn(Optional.of(order(OrderStatus.PAYING, 100L)));

        assertThatThrownBy(() -> service.close(1L, "ORD-1"))
                .isInstanceOf(BizException.class)
                .hasMessage("支付中订单不能直接关闭，请先查询支付结果");
    }

    private OrderRecord order(OrderStatus status, long price) {
        LocalDateTime now = LocalDateTime.now();
        return new OrderRecord(1L, "ORD-1", 1L, 1L, "物理必修一", price, "CNY", status,
                null, null, "req", null, now.plusMinutes(15), null, null, null,
                0, null, now, now);
    }
}
