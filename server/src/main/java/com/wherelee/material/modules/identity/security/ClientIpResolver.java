package com.wherelee.material.modules.identity.security;

import com.wherelee.material.modules.platform.config.SecurityProperties;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 客户端 IP 解析。仅在 trust-forwarded-headers=true 时读取转发头，且约定边缘代理
 * **覆盖**写入 X-Forwarded-For（见 web/nginx.conf、deploy/Caddyfile.example），
 * 因此取第一个合法值即真实客户端；代理若采用追加语义需另行配置可信代理链。
 */
@Component
public class ClientIpResolver {

    private final SecurityProperties properties;

    public ClientIpResolver(SecurityProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        if (properties.trustForwardedHeaders()) {
            String forwarded = firstValid(request.getHeader("X-Forwarded-For"));
            if (forwarded != null) {
                return forwarded;
            }
            String realIp = valid(request.getHeader("X-Real-IP"));
            if (realIp != null) {
                return realIp;
            }
        }
        String remote = valid(request.getRemoteAddr());
        return remote == null ? "unknown" : remote;
    }

    private String firstValid(String value) {
        if (value == null) {
            return null;
        }
        for (String part : value.split(",")) {
            String candidate = valid(part);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private String valid(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.isEmpty() || candidate.length() > 64 || "unknown".equalsIgnoreCase(candidate)) {
            return null;
        }
        return candidate;
    }
}
