package com.wherelee.edu.modules.operations.alert;

import com.wherelee.edu.modules.commerce.payment.PaymentProperties;
import com.wherelee.edu.modules.commerce.payment.PaymentRepository;
import com.wherelee.edu.modules.platform.alert.AlertPublisher;
import com.wherelee.edu.modules.platform.resilience.LocalFallbackCache;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentAlertJobTest {

    @Test
    void emitsPaidUnfulfilledAlert() {
        AlertProperties properties = new AlertProperties(
                true, null, 10, 5, 10, 0.8, 20, 30, 300000, 60000, 1, 15, 1);
        PaymentProperties paymentProperties = new PaymentProperties(
                "alipay", false, null, 15, 60, 50, 60000, 30000, 5, 30);
        PaymentRepository repository = mock(PaymentRepository.class);
        AlertNotifier notifier = mock(AlertNotifier.class);
        AlertPublisher publisher = mock(AlertPublisher.class);
        LocalFallbackCache dedup = mock(LocalFallbackCache.class);
        when(repository.countPaidUnfulfilledBefore(any())).thenReturn(1L);
        when(repository.countPayingBefore(any())).thenReturn(0L);
        when(repository.countRefundRetryExhausted(5)).thenReturn(0L);
        when(dedup.reserve(anyString(), any(Duration.class))).thenReturn(true);
        when(notifier.send(anyString())).thenReturn(new AlertNotifier.DeliveryResult(true, null));

        new PaymentAlertJob(properties, paymentProperties, repository, notifier, publisher, dedup).evaluate();

        verify(publisher).publish(
                "PAYMENT_PAID_UNFULFILLED", "HIGH",
                "已支付但未履约订单 1 笔", true, null);
    }
}
