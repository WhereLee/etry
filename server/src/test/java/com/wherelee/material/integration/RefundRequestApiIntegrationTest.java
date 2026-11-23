package com.wherelee.material.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherelee.material.modules.commerce.api.OrderDtos;
import com.wherelee.material.modules.commerce.cart.CartCheckoutService;
import com.wherelee.material.modules.commerce.cart.CartDtos;
import com.wherelee.material.modules.commerce.cart.CartRepository;
import com.wherelee.material.modules.commerce.order.OrderStatus;
import com.wherelee.material.modules.commerce.order.RefundRequestService;
import com.wherelee.material.modules.entitlement.api.ProductTypes;
import com.wherelee.material.modules.identity.rbac.RbacRepository;
import com.wherelee.material.modules.material.MaterialRecord;
import com.wherelee.material.modules.material.MaterialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段6补充 HTTP 层验证：买家申请整单退款 → 客服处理（同意走退款内核/拒绝留痕/拒绝后可再申请）。
 * PRD §2 买家「申请整单退款」、客服「处理退款申请」。
 */
@AutoConfigureMockMvc
class RefundRequestApiIntegrationTest extends IntegrationTestSupport {

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
    @Autowired private CartRepository cartRepository;
    @Autowired private CartCheckoutService cartCheckoutService;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private RefundRequestService refundRequestService;

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

    private long insertMaterial(String title) {
        return materialRepository.insert(new MaterialRecord(
                null, title, "EXAM_PAPER", "SENIOR", "数学", "退款申请集成资料",
                990, "CNY", 10, 3, null, "material/refund-request.pdf", MaterialRecord.ON_SALE,
                null, null, null, false));
    }

    private String purchase(long buyerId, long materialId) {
        cartRepository.add(buyerId, materialId);
        OrderDtos.OrderView order = cartCheckoutService.checkout(
                buyerId, new CartDtos.CheckoutRequest(List.of(materialId), nextRequestId()));
        paymentService.pay(buyerId, order.orderNo());
        paymentService.acceptVerified(successNotification(order.orderNo()));
        drainOutbox();
        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.FULFILLED);
        return order.orderNo();
    }

    private MvcResult submitRequest(String token, String orderNo, String reason) throws Exception {
        return mockMvc.perform(post("/api/orders/" + orderNo + "/refund-request")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + reason + "\"}"))
                .andReturn();
    }

    private long requestId(String token, String orderNo) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/orders/" + orderNo + "/refund-request")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    @Test
    void buyerRequestThenCustomerServiceApproves() throws Exception {
        String buyer = register("it_rr_buyer");
        long buyerId = userId("it_rr_buyer");
        String cs = tokenWithRole("it_rr_cs", "CUSTOMER_SERVICE");
        long materialId = insertMaterial("退款申请试卷A");
        String orderNo = purchase(buyerId, materialId);

        mockMvc.perform(get("/api/orders/" + orderNo + "/refund-request")
                        .header("Authorization", "Bearer " + buyer))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));

        assertThat(submitRequest(buyer, orderNo, "买错了").getResponse().getStatus()).isEqualTo(200);
        mockMvc.perform(get("/api/orders/" + orderNo + "/refund-request")
                        .header("Authorization", "Bearer " + buyer))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reason").value("买错了"));
        assertThat(submitRequest(buyer, orderNo, "再申请").getResponse().getStatus()).isEqualTo(400);

        String other = register("it_rr_other");
        assertThat(submitRequest(other, orderNo, "别人的单").getResponse().getStatus()).isEqualTo(404);

        mockMvc.perform(get("/api/admin/refund-requests")
                        .header("Authorization", "Bearer " + cs)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.records[0].orderNo").value(orderNo))
                .andExpect(jsonPath("$.records[0].username").value("it_rr_buyer"))
                .andExpect(jsonPath("$.records[0].orderStatus").value("FULFILLED"));
        mockMvc.perform(get("/api/admin/refund-requests").header("Authorization", "Bearer " + buyer))
                .andExpect(status().isForbidden());

        long id = requestId(buyer, orderNo);
        mockMvc.perform(post("/api/admin/refund-requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + cs))
                .andExpect(status().isOk());
        drainOutbox();

        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().status()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(entitlementRepository.hasActive(buyerId, materialId, ProductTypes.MATERIAL)).isFalse();
        mockMvc.perform(get("/api/orders/" + orderNo + "/refund-request")
                        .header("Authorization", "Bearer " + buyer))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(count("t_refund", null)).isEqualTo(1);
        assertThat(count("t_admin_audit_log",
                "action = 'REFUND_REQUEST_APPROVE' AND target_id = ? AND success = 1", id)).isEqualTo(1);

        mockMvc.perform(post("/api/admin/refund-requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + cs))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("退款申请已处理"));
    }

    @Test
    void rejectKeepsEntitlementAndAllowsReapply() throws Exception {
        String buyer = register("it_rr_buyer2");
        long buyerId = userId("it_rr_buyer2");
        String cs = tokenWithRole("it_rr_cs2", "CUSTOMER_SERVICE");
        long materialId = insertMaterial("退款申请试卷B");
        String orderNo = purchase(buyerId, materialId);

        submitRequest(buyer, orderNo, "不想要了");
        long id = requestId(buyer, orderNo);
        mockMvc.perform(post("/api/admin/refund-requests/" + id + "/reject")
                        .header("Authorization", "Bearer " + cs)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"资料已下载\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders/" + orderNo + "/refund-request")
                        .header("Authorization", "Bearer " + buyer))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.handleNote").value("资料已下载"));
        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().status()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(entitlementRepository.hasActive(buyerId, materialId, ProductTypes.MATERIAL)).isTrue();
        assertThat(count("t_refund", null)).isZero();
        assertThat(count("t_admin_audit_log",
                "action = 'REFUND_REQUEST_REJECT' AND target_id = ? AND success = 1", id)).isEqualTo(1);

        assertThat(submitRequest(buyer, orderNo, "还是想退").getResponse().getStatus()).isEqualTo(200);
        mockMvc.perform(get("/api/orders/" + orderNo + "/refund-request")
                        .header("Authorization", "Bearer " + buyer))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reason").value("还是想退"));

        mockMvc.perform(post("/api/admin/refund-requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + cs))
                .andExpect(status().isOk());
        drainOutbox();
        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().status()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void permissionsAndValidation() throws Exception {
        String operator = tokenWithRole("it_rr_op", "OPERATOR");
        String finance = tokenWithRole("it_rr_fin", "FINANCE");
        String cs = tokenWithRole("it_rr_cs3", "CUSTOMER_SERVICE");
        String buyer = register("it_rr_buyer3");
        long buyerId = userId("it_rr_buyer3");
        long materialId = insertMaterial("退款申请试卷C");
        String orderNo = purchase(buyerId, materialId);

        mockMvc.perform(get("/api/admin/refund-requests")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/refund-requests").header("Authorization", "Bearer " + operator))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/refund-requests").header("Authorization", "Bearer " + finance))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/refund-requests")
                        .header("Authorization", "Bearer " + cs)
                        .param("status", "DELETED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("非法申请状态"));
        mockMvc.perform(post("/api/admin/refund-requests/999999/approve")
                        .header("Authorization", "Bearer " + cs))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("退款申请不存在"));

        mockMvc.perform(post("/api/orders/" + orderNo + "/refund-request")
                        .header("Authorization", "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());

        long unpaidMaterial = insertMaterial("未支付资料");
        cartRepository.add(buyerId, unpaidMaterial);
        OrderDtos.OrderView unpaid = cartCheckoutService.checkout(
                buyerId, new CartDtos.CheckoutRequest(List.of(unpaidMaterial), nextRequestId()));
        assertThat(submitRequest(buyer, unpaid.orderNo(), "还没付钱").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void concurrentApproveRefundsExactlyOnce() throws Exception {
        String buyer = register("it_rr_buyer4");
        long buyerId = userId("it_rr_buyer4");
        long materialId = insertMaterial("退款申请试卷D");
        String orderNo = purchase(buyerId, materialId);
        submitRequest(buyer, orderNo, "并发同意");
        long id = requestId(buyer, orderNo);

        var results = runConcurrently(6, () -> {
            refundRequestService.approve(id, fx.adminId());
            return Boolean.TRUE;
        });

        long succeeded = results.stream().filter(result -> result.error() == null).count();
        assertThat(succeeded).isEqualTo(1);
        drainOutbox();
        assertThat(count("t_refund", null)).isEqualTo(1);
        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().status()).isEqualTo(OrderStatus.REFUNDED);
    }
}