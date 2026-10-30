package com.wherelee.etry.auth;

import com.wherelee.etry.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("0123456789abcdef0123456789abcdef");
        properties.setExpireDays(7);
        jwtService = new JwtService(properties);
    }

    @Test
    void issuedTokenCarriesUserIdAndVersion() {
        String token = jwtService.issue(42L, 3);
        Claims claims = jwtService.parse(token);

        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("ver", Integer.class)).isEqualTo(3);
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.issue(1L, 0);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(jwtService.parse(tampered)).isNull();
    }

    @Test
    void garbageIsRejectedInsteadOfThrowing() {
        assertThat(jwtService.parse("not-a-token")).isNull();
        assertThat(jwtService.parse("")).isNull();
    }
}
