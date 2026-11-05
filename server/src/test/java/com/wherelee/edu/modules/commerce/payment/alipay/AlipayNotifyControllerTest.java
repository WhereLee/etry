package com.wherelee.edu.modules.commerce.payment.alipay;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AlipayNotifyControllerTest {

    @Test
    void rejectsOversizedNotificationWithoutCallingService() throws Exception {
        AlipayNotifyService service = mock(AlipayNotifyService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(new byte[64 * 1024 + 1]);

        String result = new AlipayNotifyController(service).notify(request);

        assertThat(result).isEqualTo("failure");
        verifyNoInteractions(service);
    }

    @Test
    void passesBoundedNotificationToService() throws Exception {
        AlipayNotifyService service = mock(AlipayNotifyService.class);
        when(service.handle(any())).thenReturn("success");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("app_id=test".getBytes());

        assertThat(new AlipayNotifyController(service).notify(request)).isEqualTo("success");
    }
}
