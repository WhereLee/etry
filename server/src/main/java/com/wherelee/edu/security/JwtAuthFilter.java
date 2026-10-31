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
        if (isPublicPath(path)) {
            // 公开路径：带 token 就认一下身份（课程详情的 favorited 靠它），
            // 没带或无效都当匿名，绝不返回 401。
            resolveIdentityQuietly(request);
            try {
                chain.doFilter(request, response);
            } finally {
                UserContext.clear();
                permissionService.clear();
            }
            return;
        }
        if (!path.startsWith("/api/")) {
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

            // 4. 管理员开的号 / 密码被重置过：改密之前不放开其他接口，
            //    否则「首次必须改密」只是一个摆设
            if (user.needsPasswordChange() && !isAllowedBeforePasswordChange(path)) {
                writeError(response, ErrorCode.MUST_CHANGE_PASSWORD, "请先修改初始密码");
                return;
            }

            // 5. 角色每次查库，保证权限变更立即生效
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
     * 公开路径：完全匹配的白名单 + 公告前缀 + 讲师主页前缀 + 课程列表与详情。
     * /api/teachers 带 s，不会误伤 /api/teacher/apply（那是讲师本人操作，必须登录）。
     * 同理 /api/admin/notices 不会被 /api/notices 前缀命中。
     */
    private boolean isPublicPath(String path) {
        return WHITE_LIST.contains(path)
                || path.startsWith("/api/notices")
                || path.startsWith("/api/teachers")
                // 标签列表给前台筛选用，和 /api/admin/tags 不冲突
                || path.startsWith("/api/tags")
                // 接口文档：本身不带业务数据，要调通接口还得自己拿 token
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || isPublicCoursePath(path);
    }

    /**
     * 课程列表和详情允许匿名访问：分享出去的课程链接要能打开，
     * 潜在学员也得先看看有什么课再决定注册。
     *
     * 不能用前缀匹配：/api/courses 一前缀会把播放、评价、问答全放出去。
     * 这里只认「/api/courses」和「/api/courses/{数字}」两种形态。
     */
    private boolean isPublicCoursePath(String path) {
        if ("/api/courses".equals(path)) {
            return true;
        }
        String prefix = "/api/courses/";
        if (!path.startsWith(prefix)) {
            return false;
        }
        String rest = path.substring(prefix.length());
        return !rest.isEmpty() && rest.chars().allMatch(Character::isDigit);
    }

    /**
     * 强制改密期间仍然允许的接口：
     * 改密本身、看自己信息（前端要知道当前是谁才能决定跳哪）、登出（发现登错号能退出）。
     */
    private boolean isAllowedBeforePasswordChange(String path) {
        return "/api/auth/password".equals(path)
                || "/api/auth/me".equals(path)
                || "/api/auth/logout".equals(path);
    }

    /**
     * 公开路径上的「尽力识别」：解析成功就写入 UserContext，任何一步失败都静默当匿名。
     * 与下面强制认证的区别：这里绝不写错误响应——链接是分享给未登录的人看的。
     */
    private void resolveIdentityQuietly(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            return;
        }
        try {
            Claims claims = jwtService.parse(header.substring(BEARER.length()));
            String jti = claims.getId();
            if (blacklistService.isBlacklisted(jti)) {
                return;
            }
            Long userId = Long.valueOf(claims.getSubject());
            User user = userMapper.findById(userId);
            if (user == null || !user.isActive()) {
                return;
            }
            Integer version = claims.get("ver", Integer.class);
            if (version == null || !version.equals(user.getTokenVersion())) {
                return;
            }
            List<String> roles = roleMapper.findCodesByUserId(userId);
            UserContext.set(user.getId(), user.getUsername(), roles, jti, claims.getExpiration().toInstant());
            MDC.put(MdcTraceFilter.USER_ID, String.valueOf(userId));
        } catch (Exception e) {
            // 公开路径上的无效 token 直接忽略
        }
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(errorCode, message)));
    }
}
