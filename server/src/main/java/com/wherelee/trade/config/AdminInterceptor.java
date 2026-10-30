package com.wherelee.trade.config;

import com.wherelee.trade.auth.UserContext;
import com.wherelee.trade.common.BizException;
import com.wherelee.trade.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * /api/admin/** 下的接口只允许 ADMIN 角色访问。
 * M0 还没有后台接口，机制先建好，后续后台管理直接挂上来。
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!UserContext.isAdmin()) {
            throw BizException.of(ErrorCode.FORBIDDEN);
        }
        return true;
    }
}
