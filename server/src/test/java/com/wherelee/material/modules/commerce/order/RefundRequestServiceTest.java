package com.wherelee.material.modules.commerce.order;

import com.wherelee.material.modules.commerce.api.RefundRequestDtos;
import com.wherelee.material.modules.commerce.payment.RefundService;
import com.wherelee.material.modules.platform.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundRequestServiceTest {

    @Mock
    private RefundRequestRepository repository;
    @Mock
    private OrderService orderService;
    @Mock
    private RefundService refundService;

    private RefundRequestService service;

    @BeforeEach
    void setUp() {
        service = new RefundRequestService(repository, orderService, refundService);
    }

    private static OrderRecord order(OrderStatus status) {
        return new OrderRecord(10L, "ORD1", 2L, 0L, "MATERIAL", "资料订单", 990L, "CNY",
                status, "MOCK", "trade-1", "req-1", "2:0", LocalDateTime.now().plusMinutes(15),
                LocalDateTime.now(), LocalDateTime.now(), null, 0, null,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private static RefundRequestRecord request(String status) {
        return new RefundRequestRecord(5L, 10L, "ORD1", 2L, "买错了", status,
                null, null, null, LocalDateTime.now());
    }

    @Test
    void requestRejectsUnrefundableOrder() {
        when(orderService.requireOwned(2L, "ORD1")).thenReturn(order(OrderStatus.CREATED));

        assertThatThrownBy(() -> service.request(2L, "ORD1", "买错了"))
                .isInstanceOf(BizException.class)
                .hasMessage("当前订单状态不能退款");

        verify(repository, never()).insert(anyLong(), anyString(), anyLong(), anyString());
    }

    @Test
    void requestInsertsWhenAbsent() {
        when(orderService.requireOwned(2L, "ORD1")).thenReturn(order(OrderStatus.FULFILLED));
        when(repository.findByOrderId(10L)).thenReturn(Optional.empty(), Optional.of(request("PENDING")));

        RefundRequestDtos.BuyerView view = service.request(2L, "ORD1", "买错了");

        assertThat(view.status()).isEqualTo("PENDING");
        verify(repository).insert(10L, "ORD1", 2L, "买错了");
    }

    @Test
    void duplicatePendingRejected() {
        when(orderService.requireOwned(2L, "ORD1")).thenReturn(order(OrderStatus.FULFILLED));
        when(repository.findByOrderId(10L)).thenReturn(Optional.of(request("PENDING")));

        assertThatThrownBy(() -> service.request(2L, "ORD1", "再申请"))
                .isInstanceOf(BizException.class)
                .hasMessage("退款申请正在处理中，请耐心等待");
    }

    @Test
    void concurrentInsertFallsBackToPendingMessage() {
        when(orderService.requireOwned(2L, "ORD1")).thenReturn(order(OrderStatus.FULFILLED));
        when(repository.findByOrderId(10L)).thenReturn(Optional.empty());
        doThrow(new DuplicateKeyException("uk_refund_request_order"))
                .when(repository).insert(10L, "ORD1", 2L, "买错了");

        assertThatThrownBy(() -> service.request(2L, "ORD1", "买错了"))
                .isInstanceOf(BizException.class)
                .hasMessage("退款申请正在处理中，请耐心等待");
    }

    @Test
    void rejectedRequestCanBeReapplied() {
        when(orderService.requireOwned(2L, "ORD1")).thenReturn(order(OrderStatus.FULFILLED));
        when(repository.findByOrderId(10L)).thenReturn(Optional.of(request("REJECTED")), Optional.of(request("PENDING")));
        when(repository.reapply(10L, 2L, "还是想退")).thenReturn(1);

        assertThat(service.request(2L, "ORD1", "还是想退").status()).isEqualTo("PENDING");
        verify(repository).reapply(10L, 2L, "还是想退");
    }

    @Test
    void approvedRequestCannotBeReapplied() {
        when(orderService.requireOwned(2L, "ORD1")).thenReturn(order(OrderStatus.REFUNDING));

        assertThatThrownBy(() -> service.request(2L, "ORD1", "再申请"))
                .isInstanceOf(BizException.class)
                .hasMessage("当前订单状态不能退款");
    }

    @Test
    void approveClaimsThenRefunds() {
        when(repository.findById(5L)).thenReturn(Optional.of(request("PENDING")));
        when(orderService.require("ORD1")).thenReturn(order(OrderStatus.FULFILLED));
        when(repository.markHandled(5L, "APPROVED", 9L, null)).thenReturn(1);

        service.approve(5L, 9L);

        verify(refundService).refund(eq("ORD1"), eq(9L), anyString(), eq(false));
        verify(repository, never()).revertToPending(anyLong(), any());
    }

    @Test
    void approveRevertsClaimWhenRefundFails() {
        when(repository.findById(5L)).thenReturn(Optional.of(request("PENDING")));
        when(orderService.require("ORD1")).thenReturn(order(OrderStatus.FULFILLED));
        when(repository.markHandled(5L, "APPROVED", 9L, null)).thenReturn(1);
        doThrow(BizException.badRequest("当前订单状态不能退款"))
                .when(refundService).refund(anyString(), any(), anyString(), eq(false));

        assertThatThrownBy(() -> service.approve(5L, 9L)).isInstanceOf(BizException.class);

        verify(repository).revertToPending(5L, 9L);
    }

    @Test
    void approveLostClaimConflicts() {
        when(repository.findById(5L)).thenReturn(Optional.of(request("PENDING")));
        when(orderService.require("ORD1")).thenReturn(order(OrderStatus.FULFILLED));
        when(repository.markHandled(5L, "APPROVED", 9L, null)).thenReturn(0);

        assertThatThrownBy(() -> service.approve(5L, 9L))
                .isInstanceOf(BizException.class)
                .hasMessage("退款申请已处理");

        verify(refundService, never()).refund(anyString(), any(), anyString(), eq(false));
    }

    @Test
    void rejectStoresNote() {
        when(repository.findById(5L)).thenReturn(Optional.of(request("PENDING")));
        when(repository.markHandled(5L, "REJECTED", 9L, "资料已下载")).thenReturn(1);

        service.reject(5L, 9L, "资料已下载");

        verify(repository).markHandled(5L, "REJECTED", 9L, "资料已下载");
    }

    @Test
    void handledRequestCannotBeRejectedAgain() {
        when(repository.findById(5L)).thenReturn(Optional.of(request("REJECTED")));

        assertThatThrownBy(() -> service.reject(5L, 9L, "again"))
                .isInstanceOf(BizException.class)
                .hasMessage("退款申请已处理");
    }

    @Test
    void missingRequestNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(99L, 9L))
                .isInstanceOf(BizException.class)
                .hasMessage("退款申请不存在");
    }

    @Test
    void adminPageRejectsUnknownStatus() {
        assertThatThrownBy(() -> service.adminPage("DELETED", 1, 20))
                .isInstanceOf(BizException.class)
                .hasMessage("非法申请状态");
    }
}