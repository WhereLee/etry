package com.wherelee.edu.modules.platform.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersFilterTest {

    @Test
    void addsBaselineSecurityHeaders() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Content-Security-Policy")).contains("frame-ancestors 'none'");
        assertThat(response.getHeader("Strict-Transport-Security")).isNull();
    }
}
