package com.wherelee.edu.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.common.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 限制 JSON 请求体大小。
 *
 * multipart 上传由 spring.servlet.multipart 管着（2MB / 3MB），
 * 但 application/json 不受它约束：一个几百 MB 的 JSON 会被完整读进内存。
 * 这里在进入 Controller 之前只按 Content-Length 判断，不读 body，超了直接返回。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@RequiredArgsConstructor
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    /** 业务请求体都很小，1MB 足够；上传走 multipart，不受这里影响 */
    private static final long MAX_JSON_BYTES = 1024 * 1024L;

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String contentType = request.getContentType();
        boolean isJson = contentType != null
                && contentType.toLowerCase().startsWith(MediaType.APPLICATION_JSON_VALUE);
        long declaredLength = request.getContentLengthLong();
        // 返回 -1 表示 chunked 分块传输，长度事先未知。
        // 不拦的话可以无限往里灌，所以同样拒绝。
        if (isJson && (declaredLength < 0 || declaredLength > MAX_JSON_BYTES)) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.fail(ErrorCode.INVALID_PARAM, "请求体过大"));
            return;
        }
        chain.doFilter(request, response);
    }
}
