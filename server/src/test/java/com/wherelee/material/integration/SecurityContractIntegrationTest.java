package com.wherelee.material.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.security.cors-allowed-origins=https://shop.example.com")
class SecurityContractIntegrationTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;

    @Test
    void securityHeadersPresentOnPublicApi() throws Exception {
        mockMvc.perform(get("/api/materials"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'self'")));
    }

    @Test
    void corsRejectsUnknownOrigin() throws Exception {
        // 未知来源要么被 CorsFilter 直接 403 拒绝，要么 200 但不回显 ACAO；
        // 两种情况都不授予跨域许可，安全不变量是响应不含 Access-Control-Allow-Origin。
        mockMvc.perform(get("/api/materials").header("Origin", "https://evil.example"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void corsAllowsWhitelistedOriginWithCredentials() throws Exception {
        mockMvc.perform(get("/api/materials").header("Origin", "https://shop.example.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://shop.example.com"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void corsPreflightFromWhitelistedOriginSucceeds() throws Exception {
        mockMvc.perform(options("/api/materials")
                        .header("Origin", "https://shop.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://shop.example.com"))
                .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("POST")));
    }
}