package com.wherelee.edu.common.web;

import com.wherelee.edu.security.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 取请求来源 IP。审计记录和登录限流都要用，所以放在公共位置。
 *
 * X-Forwarded-For 完全由客户端填写，直连时信它等于让别人随便填自己的来源。
 * 只有明确部署在反向代理后面（trust-forwarded-headers=true）才取，
 * 且取最后一段：nginx 的 $proxy_add_x_forwarded_for 把真实地址追加在末尾。
 */
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    /** ip 列是 VARCHAR(50)，超长会让 INSERT 失败 */
    private static final int IP_MAX_LENGTH = 50;

    private final SecurityProperties securityProperties;

    public String resolve() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return resolve(attributes.getRequest());
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = null;
        if (securityProperties.trustForwardedHeaders()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] parts = forwarded.split(",");
                ip = parts[parts.length - 1].trim();
            }
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip != null && ip.length() > IP_MAX_LENGTH ? ip.substring(0, IP_MAX_LENGTH) : ip;
    }
}
