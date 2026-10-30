package com.wherelee.etry.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;

/**
 * 统一异常出口。
 * 关键点：判断“这个请求本来该返回 JSON 还是 HTML”不能只看路径前缀，
 * /s/{token}/unlock 这种不在 /api 下但返回 JSON 的接口必须返回 JSON 错误，
 * 否则前端会拿到一个 200 的错误页面，误判成成功。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Object handleBusiness(BusinessException e, HttpServletRequest request, HandlerMethod handlerMethod) {
        ErrorCode code = e.getErrorCode();
        log.info("business error code={} path={} message={}", code, request.getRequestURI(), e.getMessage());
        if (wantsJson(handlerMethod, request)) {
            return ResponseEntity.status(code.getStatus()).body(ApiResponse.fail(code, e.getMessage()));
        }
        return errorView(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidation(MethodArgumentNotValidException e, HttpServletRequest request,
                                   HandlerMethod handlerMethod) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null
                ? ErrorCode.VALIDATION_FAILED.getDefaultMessage()
                : fieldError.getDefaultMessage();
        if (wantsJson(handlerMethod, request)) {
            return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                    .body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED, message));
        }
        return errorView(message);
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception e, HttpServletRequest request, HandlerMethod handlerMethod) {
        log.error("unexpected error path={}", request.getRequestURI(), e);
        if (wantsJson(handlerMethod, request)) {
            return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                    .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR));
        }
        return errorView(ErrorCode.INTERNAL_ERROR.getDefaultMessage());
    }

    private boolean wantsJson(HandlerMethod handlerMethod, HttpServletRequest request) {
        if (handlerMethod != null) {
            if (handlerMethod.getBeanType().isAnnotationPresent(RestController.class)) {
                return true;
            }
            if (handlerMethod.hasMethodAnnotation(ResponseBody.class)) {
                return true;
            }
            if (handlerMethod.getBeanType().isAnnotationPresent(Controller.class)) {
                // 页面控制器：走 HTML
                return false;
            }
        }
        return request.getRequestURI().startsWith("/api/");
    }

    private ModelAndView errorView(String message) {
        ModelAndView view = new ModelAndView("error");
        view.addObject("message", message);
        return view;
    }
}
