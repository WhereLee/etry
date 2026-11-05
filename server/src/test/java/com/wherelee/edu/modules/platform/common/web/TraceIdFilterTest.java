package com.wherelee.edu.modules.platform.common.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    @Test
    void createsTraceIdAndCleansMdcAfterRequest() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/learning/series");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(request, response,
                (req, res) -> duringRequest.set(MDC.get(TraceIdFilter.MDC_KEY)));

        assertThat(duringRequest.get()).isNotBlank();
        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo(duringRequest.get());
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }
}
