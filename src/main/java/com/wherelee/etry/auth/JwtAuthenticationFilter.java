package com.wherelee.etry.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.wherelee.etry.user.User;
import com.wherelee.etry.user.UserRepository;
import com.wherelee.etry.user.UserStatus;

import java.io.IOException;

/**
 * 从 cookie 里取 JWT，校验版本号与黑名单后把当前用户放进 SecurityContext。
 * 每次请求都读库，保证改密、登出之后旧 token 立刻失效。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Claims claims = jwtService.parse(token);
            if (claims != null && !tokenBlacklist.contains(claims.getId())) {
                Long userId = parseUserId(claims.getSubject());
                Integer version = claims.get("ver", Integer.class);
                if (userId != null && version != null) {
                    userRepository.findById(userId)
                            .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                            .filter(user -> user.getTokenVersion() == version)
                            .ifPresent(this::authenticate);
                }
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(User user) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user.getId(), null, AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Long parseUserId(String subject) {
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String name = jwtService.cookieName();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
