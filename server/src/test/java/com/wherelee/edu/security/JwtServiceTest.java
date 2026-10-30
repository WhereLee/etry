package com.wherelee.edu.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 纯单元测试，不启动 Spring 上下文。
 * 只验签发与解析的契约：token 里带 userId、tokenVersion、jti，且过期/篡改会被拒。
 */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-at-least-32-bytes";
    private static final JwtProperties PROPERTIES = new JwtProperties(SECRET, 60);

    private final JwtService jwtService = new JwtService(PROPERTIES);

    @Test
    @DisplayName("签发的 token 能被解析回原样的 userId 和 tokenVersion")
    void shouldRoundTripUserIdAndVersion() {
        String token = jwtService.issue(42L, 3);

        Claims claims = jwtService.parse(token);
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("ver", Integer.class)).isEqualTo(3);
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    @DisplayName("每次签发的 jti 都不同，否则单点退出会误伤其他会话")
    void shouldGenerateDistinctJti() {
        String first = jwtService.parse(jwtService.issue(1L, 0)).getId();
        String second = jwtService.parse(jwtService.issue(1L, 0)).getId();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("签名被篡改的 token 解析失败")
    void shouldRejectTamperedToken() {
        String token = jwtService.issue(1L, 0);
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThatThrownBy(() -> jwtService.parse(tampered)).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("密钥太短时启动就失败，而不是运行时才发现")
    void shouldRejectShortSecret() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("short", 60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
