package com.wherelee.edu.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

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
