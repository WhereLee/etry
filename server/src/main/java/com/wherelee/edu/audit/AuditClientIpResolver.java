package com.wherelee.edu.audit;

import com.wherelee.edu.security.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 取审计记录里的来源 IP。
 *
 * X-Forwarded-For 完全由客户端填写，直连时信它等于让攻击者自己填审计 IP。
 * 只有明确部署在反向代理后面（trust-forwarded-headers=true）才取，
 * 且取最后一段：nginx 的 $proxy_add_x_forwarded_for 把真实地址追加在末尾，
 * 第一段才是客户端自己伪造的。
 */
@Component
@RequiredArgsConstructor
public class AuditClientIpResolver {

    /** ip 列是 VARCHAR(50)，超长会让 INSERT 失败，整条审计被吞掉 */
    private static final int IP_MAX_LENGTH = 50;

    private final SecurityProperties securityProperties;

    public String resolve() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
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
