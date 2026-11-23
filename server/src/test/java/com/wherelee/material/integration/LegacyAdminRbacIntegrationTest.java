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

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 鉴权一致性回归：遗留运营端改由 RBAC 守卫（系列治理按 SUPER_ADMIN 角色，教师邀请按 user:manage 权限点），
 * 撤销 RBAC 角色后即使 t_user.role 仍为 ADMIN 也必须立即失去访问。
 */
@AutoConfigureMockMvc
class LegacyAdminRbacIntegrationTest extends IntegrationTestSupport {

    private static final List<String> SUPER_ADMIN_PERMISSIONS = List.of("user:manage");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RbacRepository rbacRepository;

    private long adminUserId;
    private String token;

    @BeforeEach
    void seedLegacyAdmin() throws Exception {
        SUPER_ADMIN_PERMISSIONS.forEach(code -> rbacRepository.upsertPermission(code, code, "it"));
        SUPER_ADMIN_PERMISSIONS.forEach(code -> rbacRepository.linkRolePermission("SUPER_ADMIN", code));
        token = register("it_legacy_admin");
        adminUserId = userId("it_legacy_admin");
        // 模拟历史数据：legacy 角色为 ADMIN，真正权限来自 RBAC 授予。
        jdbc.update("UPDATE t_user SET role = 'ADMIN' WHERE id = ?", adminUserId);
        rbacRepository.grantRole(adminUserId, "SUPER_ADMIN", null);
    }

    @Test
    @DisplayName("撤销 RBAC 角色后，遗留运营端立即 403（不依赖 t_user.role）")
    void demotionTakesEffectImmediately() throws Exception {
        mockMvc.perform(get("/api/admin/content/series").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/teacher-invitations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        rbacRepository.revokeRole(adminUserId, "SUPER_ADMIN");

        mockMvc.perform(get("/api/admin/content/series").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/teacher-invitations").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
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