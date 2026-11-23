package com.wherelee.material.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherelee.material.modules.entitlement.api.EntitlementCommand;
import com.wherelee.material.modules.entitlement.api.ProductTypes;
import com.wherelee.material.modules.identity.rbac.RbacRepository;
import com.wherelee.material.modules.material.MaterialPdfFactory;
import com.wherelee.material.modules.material.MaterialRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段6 HTTP 层验证：运营资料管理、权限点矩阵（PRD §4）、客服权益视图、财务导出、写操作审计。
 */
@AutoConfigureMockMvc
class AdminMaterialApiIntegrationTest extends IntegrationTestSupport {

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
    @Autowired private EntitlementCommand entitlementCommand;
    @Autowired private MaterialPdfFactory pdfFactory;

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
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }

    private String tokenWithRole(String username, String roleCode) throws Exception {
        String token = register(username);
        long userId = userId(username);
        assertThat(rbacRepository.grantRole(userId, roleCode, null)).isTrue();
        return token;
    }

    private long userId(String username) {
        return jdbc.queryForObject("SELECT id FROM t_user WHERE username = ?", Long.class, username);
    }

    private long createMaterial(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/materials")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"category\":\"EXAM_PAPER\",\"stage\":\"SENIOR\","
                                + "\"subject\":\"数学\",\"summary\":\"集成测试\",\"priceCents\":990,"
                                + "\"pageCount\":10,\"previewPages\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saleStatus").value("OFF_SALE"))
                .andExpect(jsonPath("$.featured").value(false))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private byte[] demoPdf() {
        return pdfFactory.generateDemo(new MaterialRecord(null, "上传交付验证", "EXAM_PAPER", "SENIOR",
                "数学", "集成测试上传文件", 990, "CNY", 4, 2, null, null,
                MaterialRecord.OFF_SALE, null, null, null, false));
    }

    private void uploadPdf(String token, long id, byte[] bytes) throws Exception {
        mockMvc.perform(multipart("/api/admin/materials/" + id + "/file")
                        .file(new MockMultipartFile("file", "test.pdf", "application/pdf", bytes))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileKey").isNotEmpty());
    }

    @Test
    void operatorMaterialLifecycleWithAudit() throws Exception {
        String operator = tokenWithRole("it_s6_operator", "OPERATOR");
        long id = createMaterial(operator, "运营生命周期试卷");

        mockMvc.perform(put("/api/admin/materials/" + id + "/sale-status")
                        .header("Authorization", "Bearer " + operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleStatus\":\"ON_SALE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("上架前必须已上传文件且价格大于 0"));

        uploadPdf(operator, id, demoPdf());

        mockMvc.perform(put("/api/admin/materials/" + id + "/sale-status")
                        .header("Authorization", "Bearer " + operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleStatus\":\"ON_SALE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saleStatus").value("ON_SALE"));

        mockMvc.perform(put("/api/admin/materials/" + id)
                        .header("Authorization", "Bearer " + operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"改名后的试卷\",\"category\":\"TEXTBOOK\",\"stage\":\"JUNIOR\","
                                + "\"subject\":\"物理\",\"summary\":\"改简介\",\"priceCents\":1290,"
                                + "\"pageCount\":20,\"previewPages\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("改名后的试卷"))
                .andExpect(jsonPath("$.priceCents").value(1290))
                .andExpect(jsonPath("$.saleStatus").value("ON_SALE"));

        mockMvc.perform(put("/api/admin/materials/" + id + "/featured")
                        .header("Authorization", "Bearer " + operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"featured\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured").value(true));

        jdbc.update("""
                INSERT INTO t_material
                    (title, category, stage, subject, summary, price_cents, currency,
                     page_count, preview_pages, file_key, sale_status, featured)
                VALUES ('更晚创建的非推荐资料', 'EXAM_PAPER', 'SENIOR', '数学', 'x', 590, 'CNY',
                        10, 3, 'material/it-other.pdf', 'ON_SALE', 0)
                """);
        mockMvc.perform(get("/api/materials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("改名后的试卷"));

        mockMvc.perform(get("/api/admin/materials").header("Authorization", "Bearer " + operator)
                        .param("keyword", "改名").param("saleStatus", "ON_SALE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.records[0].id").value(id));

        assertThat(count("t_admin_audit_log", "action = 'MATERIAL_CREATE' AND target_id = ? AND success = 1", id))
                .isEqualTo(1);
        assertThat(count("t_admin_audit_log", "action = 'MATERIAL_UPLOAD' AND target_id = ?", id)).isEqualTo(1);
        assertThat(count("t_admin_audit_log", "action = 'MATERIAL_SALE_STATUS' AND target_id = ?", id)).isEqualTo(2);
        assertThat(count("t_admin_audit_log", "action = 'MATERIAL_FEATURED' AND target_id = ?", id)).isEqualTo(1);
    }

    @Test
    void permissionMatrixAcrossRoles() throws Exception {
        String buyer = register("it_s6_buyer");
        String cs = tokenWithRole("it_s6_cs", "CUSTOMER_SERVICE");
        String finance = tokenWithRole("it_s6_finance", "FINANCE");
        String superAdmin = tokenWithRole("it_s6_root", "SUPER_ADMIN");
        String operator = tokenWithRole("it_s6_op2", "OPERATOR");

        mockMvc.perform(get("/api/admin/materials")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/materials").header("Authorization", "Bearer " + buyer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("没有权限执行该操作"));
        mockMvc.perform(post("/api/admin/materials").header("Authorization", "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"category\":\"EXAM_PAPER\",\"priceCents\":100,"
                                + "\"pageCount\":10,\"previewPages\":3}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + cs))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + cs))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/materials").header("Authorization", "Bearer " + cs)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"category\":\"EXAM_PAPER\",\"priceCents\":100,"
                                + "\"pageCount\":10,\"previewPages\":3}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + finance))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/reconciliation/statements").header("Authorization", "Bearer " + finance))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/orders/NOPE/fulfill").header("Authorization", "Bearer " + finance))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/materials").header("Authorization", "Bearer " + finance))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/orders/NOPE/fulfill").header("Authorization", "Bearer " + superAdmin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/materials").header("Authorization", "Bearer " + superAdmin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + operator))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/materials").header("Authorization", "Bearer " + operator))
                .andExpect(status().isOk());
    }

    @Test
    void uploadRejectsNonPdfAndEmptyFile() throws Exception {
        String operator = tokenWithRole("it_s6_op3", "OPERATOR");
        long id = createMaterial(operator, "上传校验资料");

        mockMvc.perform(multipart("/api/admin/materials/" + id + "/file")
                        .file(new MockMultipartFile("file", "evil.exe", "application/octet-stream",
                                "MZ-not-a-pdf".getBytes()))
                        .header("Authorization", "Bearer " + operator))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("仅支持 PDF 文件"));

        mockMvc.perform(multipart("/api/admin/materials/" + id + "/file")
                        .file(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]))
                        .header("Authorization", "Bearer " + operator))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("文件为空"));

        assertThat(count("t_material", "id = ? AND file_key IS NULL", id)).isEqualTo(1);
    }

    @Test
    void uploadedFileIsDeliveredToBuyer() throws Exception {
        String operator = tokenWithRole("it_s6_op4", "OPERATOR");
        long id = createMaterial(operator, "上传交付验证");
        uploadPdf(operator, id, demoPdf());
        mockMvc.perform(put("/api/admin/materials/" + id + "/sale-status")
                        .header("Authorization", "Bearer " + operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleStatus\":\"ON_SALE\"}"))
                .andExpect(status().isOk());

        String buyer = register("it_s6_delivery_buyer");
        long buyerId = userId("it_s6_delivery_buyer");
        entitlementCommand.replaceManual(buyerId, ProductTypes.MATERIAL, List.of(id));

        byte[] downloaded = mockMvc.perform(get("/api/library/materials/" + id + "/download")
                        .header("Authorization", "Bearer " + buyer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
    }

    @Test
    void exportsProduceXlsxAndRequirePermission() throws Exception {
        String finance = tokenWithRole("it_s6_fin2", "FINANCE");
        String buyer = register("it_s6_export_buyer");

        byte[] orders = mockMvc.perform(get("/api/admin/orders/export")
                        .header("Authorization", "Bearer " + finance))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(orders).startsWith((byte) 'P', (byte) 'K');

        byte[] breaks = mockMvc.perform(get("/api/admin/reconciliation/runs/999/breaks/export")
                        .header("Authorization", "Bearer " + finance))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(breaks).startsWith((byte) 'P', (byte) 'K');

        mockMvc.perform(get("/api/admin/orders/export").header("Authorization", "Bearer " + buyer))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerServiceViewsUserEntitlements() throws Exception {
        String operator = tokenWithRole("it_s6_op5", "OPERATOR");
        long id = createMaterial(operator, "客服权益视图资料");
        String cs = tokenWithRole("it_s6_cs2", "CUSTOMER_SERVICE");
        String buyer = register("it_s6_ent_buyer");
        long buyerId = userId("it_s6_ent_buyer");
        entitlementCommand.replaceManual(buyerId, ProductTypes.MATERIAL, List.of(id));

        mockMvc.perform(get("/api/admin/users/" + buyerId + "/entitlements")
                        .header("Authorization", "Bearer " + cs))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productType").value("MATERIAL"))
                .andExpect(jsonPath("$[0].productId").value(id))
                .andExpect(jsonPath("$[0].title").value("客服权益视图资料"));

        mockMvc.perform(get("/api/admin/users/" + buyerId + "/entitlements")
                        .header("Authorization", "Bearer " + buyer))
                .andExpect(status().isForbidden());
    }

    @Test
    void saleStatusAndTargetValidation() throws Exception {
        String operator = tokenWithRole("it_s6_op6", "OPERATOR");
        long id = createMaterial(operator, "状态校验资料");

        mockMvc.perform(put("/api/admin/materials/" + id + "/sale-status")
                        .header("Authorization", "Bearer " + operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleStatus\":\"DELETED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("非法上架状态"));

        mockMvc.perform(put("/api/admin/materials/999999/featured")
                        .header("Authorization", "Bearer " + operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"featured\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("资料不存在"));

        mockMvc.perform(post("/api/admin/materials")
                        .header("Authorization", "Bearer " + operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"category\":\"BAD\",\"priceCents\":100,"
                                + "\"pageCount\":10,\"previewPages\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("非法分类"));
    }
}