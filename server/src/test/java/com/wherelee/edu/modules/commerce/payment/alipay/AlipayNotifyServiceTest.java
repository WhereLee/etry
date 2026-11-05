package com.wherelee.edu.modules.commerce.payment.alipay;

import com.alipay.api.internal.util.AlipaySignature;
import com.wherelee.edu.modules.platform.alert.AlertPublisher;
import com.wherelee.edu.modules.platform.tracing.TraceSpans;
import com.wherelee.edu.modules.commerce.order.OrderRecord;
import com.wherelee.edu.modules.commerce.order.OrderRepository;
import com.wherelee.edu.modules.commerce.order.OrderStatus;
import com.wherelee.edu.modules.commerce.payment.PaymentAttemptRecord;
import com.wherelee.edu.modules.commerce.payment.PaymentConfirmationService;
import com.wherelee.edu.modules.commerce.payment.PaymentMetrics;
import com.wherelee.edu.modules.commerce.payment.PaymentNotification;
import com.wherelee.edu.modules.commerce.payment.PaymentRepository;
import com.wherelee.edu.modules.commerce.payment.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlipayNotifyServiceTest {

    private static final String APP_ID = "2021000000000000";
    private static final String SELLER_ID = "2088000000000000";

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentService paymentService;
    @Mock private PaymentMetrics metrics;
    @Mock private AlertPublisher alertPublisher;

    private AlipayNotifyService service;
    private String privateKey;
    private final LocalDateTime now = LocalDateTime.now();

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        AlipayProperties properties = new AlipayProperties(
                APP_ID, privateKey, publicKey, SELLER_ID,
                "https://openapi.alipay.com/gateway.do",
                "https://whereri.site/api/payments/alipay/notify",
                "https://whereri.site/learn/orders",
                "UTF-8", "RSA2", "json", 5000, 15000);
        service = new AlipayNotifyService(properties, paymentRepository, orderRepository,
                paymentService, metrics, alertPublisher, TraceSpans.noop());
    }

    @Test
    void validNotifyConfirmsPaymentOnce() throws Exception {
        stubOrderLookup();
        when(paymentService.acceptVerified(any())).thenReturn(
                new PaymentConfirmationService.ConfirmationResult(true, false, "支付已确认", order(OrderStatus.PAID)));

        String result = service.handle(signedBody(baseParams("TRADE_SUCCESS")));

        assertThat(result).isEqualTo("success");
        ArgumentCaptor<PaymentNotification> captor = ArgumentCaptor.forClass(PaymentNotification.class);
        verify(paymentService).acceptVerified(captor.capture());
        PaymentNotification notification = captor.getValue();
        assertThat(notification.provider()).isEqualTo("ALIPAY");
        assertThat(notification.eventKey()).isEqualTo("NOTIFY-1");
        assertThat(notification.orderNo()).isEqualTo("ORD1");
        assertThat(notification.eventType()).isEqualTo("PAYMENT_SUCCESS");
        assertThat(notification.amountCents()).isEqualTo(1);
        assertThat(notification.tradeNo()).isEqualTo("2026110522001400001");
        verify(paymentRepository).updateProviderState(eq(1L), eq("TRADE_SUCCESS"), eq(1L), any());
    }

    @Test
    void tamperedAmountFailsVerification() throws Exception {
        Map<String, String> params = baseParams("TRADE_SUCCESS");
        byte[] body = signedBody(params);
        String tampered = new String(body, StandardCharsets.UTF_8).replace("total_amount=0.01", "total_amount=9.99");

        String result = service.handle(tampered.getBytes(StandardCharsets.UTF_8));

        assertThat(result).isEqualTo("failure");
        verify(metrics).callbackInvalid();
        verifyNoInteractions(paymentService);
    }

    @Test
    void wrongAppIdIsRejected() throws Exception {
        Map<String, String> params = baseParams("TRADE_SUCCESS");
        params.put("app_id", "2021999999999999");

        assertThat(service.handle(signedBody(params))).isEqualTo("failure");
        verifyNoInteractions(paymentService);
    }

    @Test
    void wrongSellerIdIsRejected() throws Exception {
        Map<String, String> params = baseParams("TRADE_SUCCESS");
        params.put("seller_id", "2088999999999999");

        assertThat(service.handle(signedBody(params))).isEqualTo("failure");
        verifyNoInteractions(paymentService);
    }

    @Test
    void closedTradeMapsToPaymentFailed() throws Exception {
        stubOrderLookup();
        when(paymentService.acceptVerified(any())).thenReturn(
                new PaymentConfirmationService.ConfirmationResult(true, false, "支付失败已入账", order(OrderStatus.PAY_FAILED)));

        assertThat(service.handle(signedBody(baseParams("TRADE_CLOSED")))).isEqualTo("success");
        ArgumentCaptor<PaymentNotification> captor = ArgumentCaptor.forClass(PaymentNotification.class);
        verify(paymentService).acceptVerified(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    void intermediateStatusIsAckedWithoutPersisting() throws Exception {
        assertThat(service.handle(signedBody(baseParams("WAIT_BUYER_PAY")))).isEqualTo("success");
        verifyNoInteractions(paymentRepository, orderRepository, paymentService);
    }

    @Test
    void unknownOutTradeNoIsAckedAndAlerted() throws Exception {
        when(paymentRepository.findAttemptByProviderTradeNo("ORD1_1")).thenReturn(Optional.empty());

        assertThat(service.handle(signedBody(baseParams("TRADE_SUCCESS")))).isEqualTo("success");
        verify(alertPublisher).publish(eq("PAYMENT_UNKNOWN_ORDER"), eq("ERROR"), anyString(), eq(false), anyString());
        verifyNoInteractions(paymentService);
    }

    @Test
    void duplicateNotifyStillAcks() throws Exception {
        stubOrderLookup();
        when(paymentService.acceptVerified(any())).thenReturn(
                new PaymentConfirmationService.ConfirmationResult(true, true, "重复支付事件已忽略", order(OrderStatus.FULFILLED)));

        assertThat(service.handle(signedBody(baseParams("TRADE_SUCCESS")))).isEqualTo("success");
    }

    private void stubOrderLookup() {
        when(paymentRepository.findAttemptByProviderTradeNo("ORD1_1")).thenReturn(Optional.of(attempt()));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order(OrderStatus.PAYING)));
    }

    private Map<String, String> baseParams(String tradeStatus) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", APP_ID);
        params.put("charset", "utf-8");
        params.put("notify_id", "NOTIFY-1");
        params.put("notify_type", "trade_status_sync");
        params.put("out_trade_no", "ORD1_1");
        params.put("trade_no", "2026110522001400001");
        params.put("trade_status", tradeStatus);
        params.put("total_amount", "0.01");
        params.put("seller_id", SELLER_ID);
        params.put("subject", "物理必修一");
        return params;
    }

    private byte[] signedBody(Map<String, String> params) throws Exception {
        String content = AlipaySignature.getSignCheckContentV1(params);
        String sign = AlipaySignature.rsaSign(content, privateKey, "UTF-8", "RSA2");
        params.put("sign", sign);
        params.put("sign_type", "RSA2");
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                    .append('&');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private PaymentAttemptRecord attempt() {
        return new PaymentAttemptRecord(1L, 1L, 1, "ALIPAY", "PROCESSING", "ORD1_1",
                "CREATED", 1L, "digest", null, null, null, now, null);
    }

    private OrderRecord order(OrderStatus status) {
        return new OrderRecord(1L, "ORD1", 1L, 1L, "物理必修一", 1L, "CNY", status,
                "ALIPAY", "2026110522001400001", "req", null, now.plusMinutes(15), null, null, null,
                0, null, now, now);
    }
}
