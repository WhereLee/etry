package com.wherelee.material.modules.identity.security;

import com.wherelee.material.modules.platform.config.SecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static ClientIpResolver resolver(boolean trustForwardedHeaders) {
        return new ClientIpResolver(new SecurityProperties(
                List.of(), trustForwardedHeaders, true, 8, 40, 10));
    }

    private static MockHttpServletRequest request(String forwardedFor, String realIp) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        if (realIp != null) {
            request.addHeader("X-Real-IP", realIp);
        }
        return request;
    }

    @Test
    @DisplayName("不信任转发头时忽略 X-Forwarded-For，只认 socket 地址")
    void ignoresForwardedHeadersWhenUntrusted() {
        assertThat(resolver(false).resolve(request("1.2.3.4", "5.6.7.8")))
                .isEqualTo("10.0.0.9");
    }

    @Test
    @DisplayName("信任转发头时优先采用边缘代理覆盖写入的 X-Forwarded-For")
    void usesForwardedForWhenTrusted() {
        assertThat(resolver(true).resolve(request("1.2.3.4", "5.6.7.8")))
                .isEqualTo("1.2.3.4");
    }

    @Test
    @DisplayName("X-Forwarded-For 非法时回退 X-Real-IP")
    void fallsBackToRealIp() {
        assertThat(resolver(true).resolve(request("unknown", "5.6.7.8")))
                .isEqualTo("5.6.7.8");
    }

    @Test
    @DisplayName("转发头缺失时回退 socket 地址")
    void fallsBackToRemoteAddr() {
        assertThat(resolver(true).resolve(request(null, null)))
                .isEqualTo("10.0.0.9");
    }
}