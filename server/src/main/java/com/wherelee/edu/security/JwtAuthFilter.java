package com.wherelee.edu.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.common.web.MdcTraceFilter;
import com.wherelee.edu.rbac.PermissionService;
import com.wherelee.edu.user.RoleMapper;
import com.wherelee.edu.user.User;
import com.wherelee.edu.user.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    /** 无 token 也能访问的路径 */
    private static final Set<String> WHITE_LIST = Set.of(
            "/api/health",
            "/api/auth/login",
            "/api/auth/register",
            "/api/subjects"
    );
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final TokenBlacklistService blacklistService;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        if (isPublicPath(path) || !path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            writeError(response, ErrorCode.UNAUTHORIZED, "缺少登录凭证");
            return;
        }

        try {
            Claims claims = jwtService.parse(header.substring(BEARER.length()));

            // 1. 单点退出：jti 是否已被拉黑
            String jti = claims.getId();
            if (blacklistService.isBlacklisted(jti)) {
                writeError(response, ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
                return;
            }

            // 2. 账号是否仍存在且启用
            Long userId = Long.valueOf(claims.getSubject());
            User user = userMapper.findById(userId);
            if (user == null) {
                writeError(response, ErrorCode.UNAUTHORIZED, "账号不存在");
                return;
            }
            if (!user.isActive()) {
                writeError(response, ErrorCode.ACCOUNT_DISABLED, "账号已被禁用");
                return;
            }

            // 3. 令牌版本：改密码后旧 token 全部失效
            Integer version = claims.get("ver", Integer.class);
            if (version == null || !version.equals(user.getTokenVersion())) {
                writeError(response, ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
                return;
            }

            // 4. 角色每次查库，保证权限变更立即生效
            List<String> roles = roleMapper.findCodesByUserId(userId);

            UserContext.set(user.getId(), user.getUsername(), roles, jti, claims.getExpiration().toInstant());
            MDC.put(MdcTraceFilter.USER_ID, String.valueOf(userId));
            chain.doFilter(request, response);
        } catch (Exception e) {
            writeError(response, ErrorCode.UNAUTHORIZED, "登录凭证无效或已过期");
        } finally {
            UserContext.clear();
            // 请求级权限缓存必须跟着请求一起清，否则线程复用会串权限
            permissionService.clear();
        }
    }

    /**
     * 公开路径：完全匹配的白名单 + 公告前缀（列表与详情都不需要登录）。
     * 注意 /api/admin/notices 不会被这个前缀命中。
     */
    private boolean isPublicPath(String path) {
        return WHITE_LIST.contains(path) || path.startsWith("/api/notices");
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(errorCode, message)));
    }
}
