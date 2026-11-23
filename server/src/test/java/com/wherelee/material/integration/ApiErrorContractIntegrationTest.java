package com.wherelee.material.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 参数错误契约：校验失败/类型不匹配/缺参/请求体损坏均为 400，而非兜底 500。
 */
@AutoConfigureMockMvc
class ApiErrorContractIntegrationTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("搜索关键词为空白时返回 400")
    void blankSearchKeywordReturns400() throws Exception {
        mockMvc.perform(get("/api/materials/search").param("keyword", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("搜索缺少必需参数时返回 400")
    void missingSearchKeywordReturns400() throws Exception {
        mockMvc.perform(get("/api/materials/search"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("分页参数类型不匹配时返回 400")
    void invalidPageTypeReturns400() throws Exception {
        mockMvc.perform(get("/api/materials").param("page", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("请求体非法 JSON 时返回 400")
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":"))
                .andExpect(status().isBadRequest());
    }
}