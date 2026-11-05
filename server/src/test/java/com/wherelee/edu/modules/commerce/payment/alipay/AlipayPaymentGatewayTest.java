package com.wherelee.edu.modules.commerce.payment.alipay;

import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherelee.edu.modules.platform.common.BizException;
import com.wherelee.edu.modules.commerce.order.OrderRecord;
import com.wherelee.edu.modules.commerce.order.OrderStatus;
import com.wherelee.edu.modules.commerce.payment.PaymentAttemptRecord;
import com.wherelee.edu.modules.commerce.payment.PaymentGateway;
import com.wherelee.edu.modules.commerce.payment.RefundRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlipayPaymentGatewayTest {

    @Mock
    private AlipayClient client;

    private AlipayPaymentGateway gateway;
    private final LocalDateTime now = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        AlipayProperties properties = new AlipayProperties(
                "2021000000000000", "private-key", "public-key", "2088000000000000",
                "https://openapi.alipay.com/gateway.do",
                "https://whereri.site/api/payments/alipay/notify",
                "https://whereri.site/learn/orders",
                "UTF-8", "RSA2", "json", 5000, 15000);
        gateway = new AlipayPaymentGateway(client, properties, new ObjectMapper());
    }

    @Test
    void outTradeNoCombinesOrderNoAndAttempt() {
        assertThat(gateway.outTradeNo(order(), 2)).isEqualTo("ORD1_2");
        assertThat(gateway.channel()).isEqualTo("ALIPAY");
    }

    @Test
    void initiateReturnsCashierUrlAndSendsRequiredFields() throws Exception {
        AlipayTradePagePayResponse response = new AlipayTradePagePayResponse();
        response.setCode("10000");
        response.setBody("https://openapi.alipay.com/gateway.do?out_trade_no=ORD1_1");
        when(client.pageExecute(any(AlipayTradePagePayRequest.class), eq("GET"))).thenReturn(response);

        PaymentGateway.PaymentAction action = gateway.initiate(order(), attempt());

        assertThat(action.cashierUrl()).startsWith("https://openapi.alipay.com/gateway.do");
        ArgumentCaptor<AlipayTradePagePayRequest> captor = ArgumentCaptor.forClass(AlipayTradePagePayRequest.class);
        verify(client).pageExecute(captor.capture(), eq("GET"));
        AlipayTradePagePayRequest request = captor.getValue();
        assertThat(request.getNotifyUrl()).isEqualTo("https://whereri.site/api/payments/alipay/notify");
        assertThat(request.getReturnUrl()).isEqualTo("https://whereri.site/learn/orders/ORD1");
        assertThat(request.getBizContent())
                .contains("\"out_trade_no\":\"ORD1_1\"")
                .contains("\"total_amount\":\"0.01\"")
                .contains("\"product_code\":\"FAST_INSTANT_TRADE_PAY\"")
                .contains("\"timeout_express\":");
    }

    @Test
    void initiateFailureSurfacesAsBizException() throws Exception {
        AlipayTradePagePayResponse response = new AlipayTradePagePayResponse();
        response.setCode("40004");
        response.setSubCode("ACQ.INVALID_PARAMETER");
        response.setSubMsg("参数无效");
        when(client.pageExecute(any(AlipayTradePagePayRequest.class), eq("GET"))).thenReturn(response);

        assertThatThrownBy(() -> gateway.initiate(order(), attempt())).isInstanceOf(BizException.class);
    }

    @Test
    void queryMapsTradeSuccessToPaid() throws Exception {
        AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
        response.setCode("10000");
        response.setTradeStatus("TRADE_SUCCESS");
        response.setTotalAmount("0.01");
        response.setTradeNo("2026110522001400001");
        when(client.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);

        PaymentGateway.PaymentQueryResult result = gateway.query(order(), attempt());

        assertThat(result.status()).isEqualTo("PAID");
        assertThat(result.amountCents()).isEqualTo(1);
        assertThat(result.tradeNo()).isEqualTo("2026110522001400001");
    }

    @Test
    void queryMapsTradeClosedToFailed() throws Exception {
        AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
        response.setCode("10000");
        response.setTradeStatus("TRADE_CLOSED");
        response.setTotalAmount("0.01");
        when(client.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);

        assertThat(gateway.query(order(), attempt()).status()).isEqualTo("FAILED");
    }

    @Test
    void queryTradeNotExistIsExplicitNotFound() throws Exception {
        AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
        response.setCode("40004");
        response.setSubCode("ACQ.TRADE_NOT_EXIST");
        when(client.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);

        assertThat(gateway.query(order(), attempt()).status()).isEqualTo("NOT_FOUND");
    }

    @Test
    void queryWithoutAttemptStaysPending() {
        PaymentGateway.PaymentQueryResult result = gateway.query(order(), null);
        assertThat(result.status()).isEqualTo("PENDING");
        verifyNoInteractions(client);
    }

    @Test
    void refundWithFundChangeSucceeds() throws Exception {
        AlipayTradeRefundResponse response = new AlipayTradeRefundResponse();
        response.setCode("10000");
        response.setFundChange("Y");
        response.setTradeNo("2026110522001400001");
        when(client.execute(any(AlipayTradeRefundRequest.class))).thenReturn(response);

        PaymentGateway.RefundResult result = gateway.refund(paidOrder(), refund(), false);

        assertThat(result.success()).isTrue();
        assertThat(result.providerRefundNo()).isEqualTo("2026110522001400001");
    }

    @Test
    void refundWithoutFundChangeConfirmsByRefundQuery() throws Exception {
        AlipayTradeRefundResponse refundResponse = new AlipayTradeRefundResponse();
        refundResponse.setCode("10000");
        refundResponse.setFundChange("N");
        when(client.execute(any(AlipayTradeRefundRequest.class))).thenReturn(refundResponse);

        AlipayTradeFastpayRefundQueryResponse queryResponse = new AlipayTradeFastpayRefundQueryResponse();
        queryResponse.setCode("10000");
        queryResponse.setRefundStatus("REFUND_SUCCESS");
        queryResponse.setOutRequestNo("REF1");
        when(client.execute(any(AlipayTradeFastpayRefundQueryRequest.class))).thenReturn(queryResponse);

        PaymentGateway.RefundResult result = gateway.refund(paidOrder(), refund(), false);

        assertThat(result.success()).isTrue();
        assertThat(result.providerRefundNo()).isEqualTo("REF1");
    }

    @Test
    void simulateFailureNeverCallsAlipay() {
        PaymentGateway.RefundResult result = gateway.refund(paidOrder(), refund(), true);
        assertThat(result.success()).isFalse();
        verifyNoInteractions(client);
    }

    @Test
    void refundWithoutProviderTradeNoFails() {
        PaymentGateway.RefundResult result = gateway.refund(order(), refund(), false);
        assertThat(result.success()).isFalse();
        verifyNoInteractions(client);
    }

    private OrderRecord order() {
        return new OrderRecord(1L, "ORD1", 1L, 1L, "物理必修一", 1L, "CNY", OrderStatus.PAYING,
                "ALIPAY", null, "req", null, now.plusMinutes(15), null, null, null,
                0, null, now, now);
    }

    private OrderRecord paidOrder() {
        return new OrderRecord(1L, "ORD1", 1L, 1L, "物理必修一", 1L, "CNY", OrderStatus.PAID,
                "ALIPAY", "2026110522001400001", "req", null, now.plusMinutes(15), now, null, null,
                0, null, now, now);
    }

    private PaymentAttemptRecord attempt() {
        return new PaymentAttemptRecord(1L, 1L, 1, "ALIPAY", "PROCESSING", "ORD1_1",
                "CREATED", 1L, "digest", null, null, null, now, null);
    }

    private RefundRecord refund() {
        return new RefundRecord(1L, "REF1", 1L, 1L, "PROCESSING", 0, null, null,
                "用户申请退款", 9L, now, null, null);
    }
}
