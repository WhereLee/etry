package com.wherelee.trade.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherelee.trade.common.ApiResponse;
import com.wherelee.trade.common.ErrorCode;
import com.wherelee.trade.user.User;
import com.wherelee.trade.user.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Set<String> WHITE_LIST = Set.of("/api/auth/login", "/api/auth/register");
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final TokenBlacklistService blacklistService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        if (WHITE_LIST.contains(path) || !path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            writeUnauthorized(response, "缺少登录凭证");
            return;
        }

        try {
            Claims claims = jwtService.parse(header.substring(BEARER.length()));

            // 1. 单点退出：token 是否已进黑名单
            String jti = claims.getId();
            if (blacklistService.isBlacklisted(jti)) {
                writeUnauthorized(response, "登录已失效，请重新登录");
                return;
            }

            // 2. 批量失效：改密码后旧 token 的版本对不上
            Long userId = Long.valueOf(claims.getSubject());
            User user = userMapper.findById(userId);
            if (user == null) {
                writeUnauthorized(response, "账号不存在");
                return;
            }
            Integer tokenVersion = claims.get("ver", Integer.class);
            if (tokenVersion == null || !tokenVersion.equals(user.getTokenVersion())) {
                writeUnauthorized(response, "登录已失效，请重新登录");
                return;
            }

            UserContext.set(user.getId(), user.getUsername(), user.getRole(),
                    jti, claims.getExpiration().toInstant());
            chain.doFilter(request, response);
        } catch (Exception e) {
            writeUnauthorized(response, "登录凭证无效或已过期");
        } finally {
            UserContext.clear();
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.fail(ErrorCode.UNAUTHORIZED, message)));
    }
}
