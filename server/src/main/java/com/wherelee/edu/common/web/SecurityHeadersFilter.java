package com.wherelee.edu.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 安全响应头。解决的是「浏览器把接口响应当成网页执行」这一类问题。
 *
 * X-Content-Type-Options: nosniff  —— 禁止浏览器猜类型，JSON 不会被当成 HTML 解析
 * X-Frame-Options: DENY            —— 禁止别的站点用 iframe 套住本服务（点击劫持）
 * Referrer-Policy: no-referrer     —— 跳转时不把当前 URL 带给第三方
 * CSP (仅 /api/**)                 —— 接口只返回数据，任何资源都不允许加载
 *
 * CSP 不全局加：将来如果由本服务托管前端页面，default-src 'none' 会把页面打死，
 * 而接口响应加这个头对前端正常请求没有任何影响。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        }
        chain.doFilter(request, response);
    }
}
