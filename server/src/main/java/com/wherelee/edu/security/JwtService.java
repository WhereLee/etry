package com.wherelee.edu.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(JwtProperties properties) {
        byte[] bytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret 长度不足 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttl = Duration.ofMinutes(properties.ttlMinutes());
    }

    /**
     * token 里只放 userId 和 tokenVersion。
     * 角色和权限不入 token —— 否则管理员收回权限后，旧 token 还能用满 2 小时。
     */
    public String issue(Long userId, int tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("ver", tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
