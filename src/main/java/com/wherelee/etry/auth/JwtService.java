package com.wherelee.etry.auth;

import com.wherelee.etry.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(Long userId, int tokenVersion) {
        Instant now = Instant.now();
        Instant expireAt = now.plus(properties.getExpireDays(), ChronoUnit.DAYS);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .claim("ver", tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(key)
                .compact();
    }

    /** 解析失败（过期、签名不对、格式不对）一律返回 null */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public int expireDays() {
        return properties.getExpireDays();
    }

    public String cookieName() {
        return properties.getCookieName();
    }
}
