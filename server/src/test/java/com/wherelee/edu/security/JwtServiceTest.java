package com.wherelee.edu.security;

import com.wherelee.edu.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @Test
    void tokenCarriesAccountTokenVersion() {
        JwtService service = new JwtService(new JwtProperties(
                "01234567890123456789012345678901", 60));
        User user = new User(7L, "student", "hash", "学生", "STUDENT", "ACTIVE", false, 3);

        String token = service.createToken(user);
        JwtService.TokenPayload payload = service.parse(token);

        assertThat(payload.userId()).isEqualTo(7L);
        assertThat(payload.tokenVersion()).isEqualTo(3);
    }
}
