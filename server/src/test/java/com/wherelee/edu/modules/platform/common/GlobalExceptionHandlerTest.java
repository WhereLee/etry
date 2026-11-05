package com.wherelee.edu.modules.platform.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unknownResourceReturns404InsteadOf500() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(
                new NoResourceFoundException(HttpMethod.GET, "api/missing"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("请求的资源不存在");
    }
}
