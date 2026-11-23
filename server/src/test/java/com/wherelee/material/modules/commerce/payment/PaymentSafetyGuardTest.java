package com.wherelee.material.modules.commerce.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentSafetyGuardTest {

    private static PaymentProperties props(String provider, boolean mockEnabled) {
        return new PaymentProperties(provider, mockEnabled, "secret", 15, 15, 20, 15000, 10000, 5, 30);
    }

    private static PaymentSafetyGuard guard(PaymentProperties properties, String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return new PaymentSafetyGuard(properties, environment);
    }

    @Test
    @DisplayName("演示/测试显式启用 Mock 时允许启动")
    void allowsExplicitMockOutsideProd() {
        assertThatCode(() -> guard(props("mock", true)).verify()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("provider=mock 但未显式启用 Mock 时启动失败（fail-closed）")
    void rejectsMockWhenNotExplicitlyEnabled() {
        assertThatThrownBy(() -> guard(props("mock", false)).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Mock 支付未显式启用");
    }

    @Test
    @DisplayName("非 Mock 渠道且未启用 Mock 时允许启动")
    void allowsRealProviderWithMockDisabled() {
        assertThatCode(() -> guard(props("alipay", false)).verify()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("prod 环境下 provider=mock 一律拒绝")
    void rejectsMockProviderInProd() {
        assertThatThrownBy(() -> guard(props("mock", true), "prod").verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境禁止启用 Mock 支付");
    }

    @Test
    @DisplayName("prod 环境下即使真实渠道也不允许开启 Mock 能力")
    void rejectsMockEnabledInProd() {
        assertThatThrownBy(() -> guard(props("alipay", true), "prod").verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境禁止启用 Mock 支付");
    }

    @Test
    @DisplayName("prod 环境下真实渠道且关闭 Mock 允许启动")
    void allowsRealProviderInProd() {
        assertThatCode(() -> guard(props("alipay", false), "prod").verify()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("不支持的支付渠道启动失败")
    void rejectsUnsupportedProvider() {
        assertThatThrownBy(() -> guard(props("paypal", false)).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不支持的支付渠道");
    }
}