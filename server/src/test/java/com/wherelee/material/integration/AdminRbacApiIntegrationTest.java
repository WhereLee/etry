package com.wherelee.material.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherelee.material.modules.identity.rbac.RbacRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段6补充 HTTP 层验证：RBAC 角色管理（角色目录/用户角色回显/整体替换）与审计日志分页查询
 * （PRD §2 超级管理员、§4 权限点鉴权 + 写操作审计）。
 */
@AutoConfigureMockMvc
class AdminRbacApiIntegrationTest extends IntegrationTestSupport {

    private static final Map<String, List<String>> ROLE_PERMISSIONS = Map.of(
            "OPERATOR", List.of("material:read", "material:create", "material:update", "material:sale"),
            "CUSTOMER_SERVICE", List.of("order:read", "refund:operate", "user:read"),
            "FINANCE", List.of("order:read", "refund:operate", "recon:read", "recon:operate"),
            "SUPER_ADMIN", List.of("material:read", "material:create", "material:update", "material:sale",
                    "order:read", "order:operate", "refund:operate", "recon:read", "recon:operate",
                    "user:read", "user:manage", "audit:read")
    );

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RbacRepository rbacRepository;

    @BeforeEach
    void seedRbac() {
        ROLE_PERMISSIONS.values().stream().flatMap(List::stream).distinct()
                .forEach(code -> rbacRepository.upsertPermission(code, code, "it"));
        ROLE_PERMISSIONS.forEach((role, codes) ->
                codes.forEach(code -> rbacRepository.linkRolePermission(role, code)));
    }

    private String register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Buyer#2026x\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String tokenWithRole(String username, String roleCode) throws Exception {
        String token = register(username);
        assertThat(rbacRepository.grantRole(userId(username), roleCode, null)).isTrue();
        return token;
    }

    private long userId(String username) {
        return jdbc.queryForObject("SELECT id FROM t_user WHERE username = ?", Long.class, username);
    }

    private MvcResult assignRoles(String token, long userId, String body) throws Exception {
        return mockMvc.perform(put("/api/admin/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    @Test
    void rolesCatalogEchoesPermissions() throws Exception {
        String root = tokenWithRole("it_rbac_root", "SUPER_ADMIN");

        MvcResult result = mockMvc.perform(get("/api/admin/roles").header("Authorization", "Bearer " + root))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode roles = objectMapper.readTree(result.getResponse().getContentAsString());
        List<String> codes = new ArrayList<>();
        roles.forEach(role -> codes.add(role.get("code").asText()));
        assertThat(codes).contains("BUYER", "OPERATOR", "CUSTOMER_SERVICE", "FINANCE", "SUPER_ADMIN");
        for (JsonNode role : roles) {
            if ("SUPER_ADMIN".equals(role.get("code").asText())) {
                List<String> permissions = new ArrayList<>();
                role.get("permissions").forEach(p -> permissions.add(p.asText()));
                assertThat(permissions).contains("user:manage", "audit:read", "order:operate");
            }
        }
    }

    @Test
    void assignRolesChangesEffectivePermissionsImmediately() throws Exception {
        String root = tokenWithRole("it_rbac_root2", "SUPER_ADMIN");
        String buyer = register("it_rbac_buyer");
        long buyerId = userId("it_rbac_buyer");

        mockMvc.perform(get("/api/admin/users/" + buyerId + "/roles").header("Authorization", "Bearer " + root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("BUYER"));
        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + buyer))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/admin/users/" + buyerId + "/roles")
                        .header("Authorization", "Bearer " + root)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[\"BUYER\",\"CUSTOMER_SERVICE\",\"BUYER\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + buyer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users/" + buyerId + "/roles").header("Authorization", "Bearer " + root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(put("/api/admin/users/" + buyerId + "/roles")
                        .header("Authorization", "Bearer " + root)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[\"BUYER\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + buyer))
                .andExpect(status().isForbidden());

        assertThat(count("t_admin_audit_log",
                "action = 'USER_ROLES_ASSIGN' AND target_id = ? AND success = 1", buyerId)).isEqualTo(2);
        assertThat(count("t_user_role", "user_id = ? AND granted_by = ?", buyerId, userId("it_rbac_root2")))
                .isEqualTo(1);
    }

    @Test
    void assignRolesValidationAndSelfGuard() throws Exception {
        String root = tokenWithRole("it_rbac_root3", "SUPER_ADMIN");
        long rootId = userId("it_rbac_root3");

        assertThat(assignRoles(root, rootId, "{\"roleCodes\":[\"BUYER\"]}").getResponse().getStatus()).isEqualTo(400);
        mockMvc.perform(put("/api/admin/users/" + rootId + "/roles")
                        .header("Authorization", "Bearer " + root)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[\"BUYER\"]}"))
                .andExpect(jsonPath("$.message").value("不能修改自己的角色"));
        assertThat(rbacRepository.findRoleCodesByUser(rootId)).containsExactlyInAnyOrder("BUYER", "SUPER_ADMIN");

        register("it_rbac_buyer2");
        long buyerId = userId("it_rbac_buyer2");
        mockMvc.perform(put("/api/admin/users/" + buyerId + "/roles")
                        .header("Authorization", "Bearer " + root)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[\"NOPE\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("角色不存在：NOPE"));
        assertThat(rbacRepository.findRoleCodesByUser(buyerId)).containsExactly("BUYER");

        mockMvc.perform(put("/api/admin/users/999999/roles")
                        .header("Authorization", "Bearer " + root)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[\"BUYER\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("用户不存在"));

        mockMvc.perform(put("/api/admin/users/" + buyerId + "/roles")
                        .header("Authorization", "Bearer " + root)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rbacEndpointsRequireUserManage() throws Exception {
        String operator = tokenWithRole("it_rbac_op", "OPERATOR");
        String cs = tokenWithRole("it_rbac_cs", "CUSTOMER_SERVICE");
        long operatorId = userId("it_rbac_op");

        mockMvc.perform(get("/api/admin/roles")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/roles").header("Authorization", "Bearer " + operator))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("没有权限执行该操作"));
        mockMvc.perform(get("/api/admin/users/" + operatorId + "/roles").header("Authorization", "Bearer " + cs))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/users/" + operatorId + "/roles")
                        .header("Authorization", "Bearer " + cs)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[\"BUYER\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditLogPageFiltersAndPermissions() throws Exception {
        String root = tokenWithRole("it_rbac_root4", "SUPER_ADMIN");
        long rootId = userId("it_rbac_root4");
        register("it_rbac_buyer3");
        long buyerId = userId("it_rbac_buyer3");

        assignRoles(root, buyerId, "{\"roleCodes\":[\"BUYER\",\"FINANCE\"]}");
        assignRoles(root, buyerId, "{\"roleCodes\":[\"GHOST\"]}");

        MvcResult result = mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + root)
                        .param("operatorId", String.valueOf(rootId))
                        .param("action", "USER_ROLES_ASSIGN")
                        .param("targetType", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.records[0].targetId").value(buyerId))
                .andReturn();
        JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        JsonNode records = page.get("records");
        assertThat(records.get(0).get("id").asLong()).isGreaterThan(records.get(1).get("id").asLong());
        assertThat(records.get(0).get("success").asBoolean()).isFalse();
        assertThat(records.get(0).get("errorMessage").asText()).contains("角色不存在");
        assertThat(records.get(1).get("success").asBoolean()).isTrue();

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + root)
                        .param("operatorId", String.valueOf(rootId))
                        .param("action", "USER_ROLES_ASSIGN")
                        .param("success", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.records[0].success").value(true));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + root)
                        .param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));

        String cs = tokenWithRole("it_rbac_cs2", "CUSTOMER_SERVICE");
        String finance = tokenWithRole("it_rbac_fin", "FINANCE");
        mockMvc.perform(get("/api/admin/audit-logs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + cs))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + finance))
                .andExpect(status().isForbidden());
    }
}