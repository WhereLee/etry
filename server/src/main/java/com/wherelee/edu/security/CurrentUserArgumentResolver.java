package com.wherelee.edu.security;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        UserContext.Principal principal = UserContext.get();
        if (principal == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        Class<?> type = parameter.getParameterType();
        if (UserContext.Principal.class.isAssignableFrom(type)) {
            return principal;
        }
        if (Long.class.isAssignableFrom(type) || long.class.isAssignableFrom(type)) {
            return principal.userId();
        }
        throw new IllegalStateException("@CurrentUser 不支持的类型: " + type.getName());
    }
}
