package com.wherelee.edu.modules.identity.security;

import com.wherelee.edu.modules.platform.config.SecurityProperties;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeaderWhenProxyIsNotTrusted() {
        ClientIpResolver resolver = new ClientIpResolver(properties(false));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.8");
    }

    @Test
    void usesFirstForwardedAddressWhenProxyIsTrusted() {
        ClientIpResolver resolver = new ClientIpResolver(properties(true));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.2");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    private SecurityProperties properties(boolean trustForwardedHeaders) {
        return new SecurityProperties(List.of("http://localhost:5175"), trustForwardedHeaders, true, 8, 40, 10);
    }
}
