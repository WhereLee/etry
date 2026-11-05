package com.wherelee.edu.modules.identity.security;

import com.wherelee.edu.modules.identity.user.User;
import com.wherelee.edu.modules.identity.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_API = Set.of(
            "/api/health",
            "/api/auth/login",
            "/api/auth/teacher-invitations/preview",
            "/api/auth/teacher-invitations/accept",
            "/api/payments/alipay/notify"
    );
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || PUBLIC_API.contains(path)) {
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
            chain.doFilter(request, response);
        } catch (Exception e) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "登录已失效，请重新登录");
        }
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
