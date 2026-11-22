package com.wherelee.material.modules.identity.security;

import com.wherelee.material.modules.identity.rbac.RbacRepository;
import com.wherelee.material.modules.identity.user.User;
import com.wherelee.material.modules.identity.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_API = Set.of(
            "/api/health",
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/teacher-invitations/preview",
            "/api/auth/teacher-invitations/accept",
            "/api/payments/alipay/notify"
    );
    private static final String PUBLIC_CATALOG_PREFIX = "/api/materials";
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RbacRepository rbacRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository, RbacRepository rbacRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.rbacRepository = rbacRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || PUBLIC_API.contains(path) || isPublicCatalog(request, path)) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
            return;
        }

        try {
            JwtService.TokenPayload payload = jwtService.parse(header.substring(BEARER.length()));
            User user = userRepository.findById(payload.userId())
                    .filter(User::isActive)
                    .orElse(null);
            if (user == null) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "账号不可用，请重新登录");
                return;
            }
            if (payload.tokenVersion() != user.tokenVersion()) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "登录已失效，请重新登录");
                return;
            }
            if (user.mustChangePassword() && !allowedBeforePasswordChange(path)) {
                writeError(response, 428, "请先修改初始密码");
                return;
            }
            request.setAttribute("userId", user.id());
            request.setAttribute("role", user.role());
            authenticate(user);
            chain.doFilter(request, response);
        } catch (Exception e) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "登录已失效，请重新登录");
        }
    }

    /** 目录/搜索/详情为游客可读；管理端资料接口走 /api/admin 前缀，不在此列。 */
    private boolean isPublicCatalog(HttpServletRequest request, String path) {
        return HttpMethod.GET.matches(request.getMethod())
                && (path.equals(PUBLIC_CATALOG_PREFIX) || path.startsWith(PUBLIC_CATALOG_PREFIX + "/"));
    }

    /** RBAC 角色 → ROLE_ 权威，权限点原样作为权威；无 RBAC 记录时回退 legacy 角色。 */
    private void authenticate(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        List<String> roleCodes = rbacRepository.findRoleCodesByUser(user.id());
        if (roleCodes.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.role()));
        } else {
            roleCodes.forEach(code -> authorities.add(new SimpleGrantedAuthority("ROLE_" + code)));
            rbacRepository.findPermissionCodesByUser(user.id())
                    .forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
        }
        var authentication = new UsernamePasswordAuthenticationToken(user.id(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private boolean allowedBeforePasswordChange(String path) {
        return "/api/auth/password".equals(path) || "/api/auth/me".equals(path);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}