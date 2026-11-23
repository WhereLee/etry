package com.wherelee.material.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherelee.material.modules.identity.rbac.RbacRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 运营订单列表回归：资料订单（product_title_snapshot）必须展示商品标题并可被关键词检索。
 */
@AutoConfigureMockMvc
class AdminOrderListIntegrationTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RbacRepository rbacRepository;

    private String adminToken;

    @BeforeEach
    void seedAdmin() throws Exception {
        rbacRepository.upsertPermission("order:read", "订单查询", "commerce");
        rbacRepository.linkRolePermission("SUPER_ADMIN", "order:read");
        adminToken = register("it_admin_order");
        rbacRepository.grantRole(userId("it_admin_order"), "SUPER_ADMIN", null);
    }

    @Test
    @DisplayName("资料订单在运营列表展示商品标题，并支持按标题关键词检索")
    void materialOrderShowsTitleAndIsSearchable() throws Exception {
        insertMaterialOrder("IT-MAT-ORDER-1", "资料订单 共2件");

        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.records[0].seriesTitle").value("资料订单 共2件"));

        mockMvc.perform(get("/api/admin/orders")
                        .param("keyword", "资料订单")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(get("/api/admin/orders")
                        .param("keyword", "不存在的关键词")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    private void insertMaterialOrder(String orderNo, String title) {
        jdbc.update("""
                INSERT INTO t_order
                    (order_no, user_id, product_type, product_title_snapshot,
                     price_cents, currency, status, client_request_id, expire_at)
                VALUES (?, ?, 'MATERIAL', ?, 1980, 'CNY', 'PAID', ?, DATE_ADD(NOW(3), INTERVAL 1 DAY))
                """, orderNo, fx.studentId(), title, "it-admin-order-" + orderNo);
    }

    private String register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Buyer#2026x\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }

    private long userId(String username) {
        return jdbc.queryForObject("SELECT id FROM t_user WHERE username = ?", Long.class, username);
    }
}