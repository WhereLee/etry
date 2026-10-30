package com.wherelee.etry.auth;

import com.wherelee.etry.config.JwtProperties;
import com.wherelee.etry.user.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * JWT 只放在 httpOnly cookie 里，前端拿不到原文，也就不存在把 token 存 localStorage 那一套。
 */
@Component
@RequiredArgsConstructor
public class TokenCookieSupport {

    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public void write(HttpServletResponse response, User user) {
        String token = jwtService.issue(user.getId(), user.getTokenVersion());
        ResponseCookie cookie = ResponseCookie
                .from(jwtProperties.getCookieName(), token)
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .path("/")
                .maxAge(Duration.ofDays(jwtProperties.getExpireDays()))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie
                .from(jwtProperties.getCookieName(), "")
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (jwtProperties.getCookieName().equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
