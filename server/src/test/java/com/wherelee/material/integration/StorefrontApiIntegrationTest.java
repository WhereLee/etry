package com.wherelee.material.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherelee.material.modules.entitlement.api.EntitlementCommand;
import com.wherelee.material.modules.entitlement.api.ProductTypes;
import com.wherelee.material.modules.material.MaterialRecord;
import com.wherelee.material.modules.material.MaterialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段3 HTTP 层验证：注册即登录、游客可读目录、购物车鉴权与加购/幂等/已购/下架分支。
 */
@AutoConfigureMockMvc
class StorefrontApiIntegrationTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private EntitlementCommand entitlementCommand;

    private long insertMaterial(String title, long priceCents, String saleStatus) {
        return materialRepository.insert(new MaterialRecord(
                null, title, "EXAM_PAPER", "SENIOR", "数学", "HTTP 集成资料",
                priceCents, "CNY", 10, 3, null, "material/http.pdf", saleStatus,
                null, null, null, false));
    }

    private String registerBuyer(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Buyer#2026x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }

    @Test
    void registerReturnsTokenAndDuplicateRejected() throws Exception {
        String token = registerBuyer("it_buyer_reg");
        assertThat(token).isNotBlank();
        assertThat(count("t_user_role ur JOIN t_role r ON r.id = ur.role_id",
                "r.code = 'BUYER' AND ur.user_id = (SELECT id FROM t_user WHERE username = 'it_buyer_reg')"))
                .isEqualTo(1);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"it_buyer_reg\",\"password\":\"Buyer#2026x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("用户名已存在"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"it_buyer_weak\",\"password\":\"12345678\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("it_buyer_reg"))
                .andExpect(jsonPath("$.roles[0]").value("BUYER"))
                .andExpect(jsonPath("$.permissions").isArray())
                .andExpect(jsonPath("$.permissions").isEmpty());
    }

    @Test
    void publicCatalogReadableWithoutToken() throws Exception {
        long onSale = insertMaterial("游客可读试卷", 990, MaterialRecord.ON_SALE);
        long offSale = insertMaterial("下架讲义", 590, MaterialRecord.OFF_SALE);

        mockMvc.perform(get("/api/materials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("游客可读试卷"));

        mockMvc.perform(get("/api/materials").param("category", "EXAM_PAPER").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/api/materials/search").param("keyword", "试卷"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("游客可读试卷"));

        mockMvc.perform(get("/api/materials/" + onSale))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("游客可读试卷"))
                .andExpect(jsonPath("$.previewPages").value(3));

        mockMvc.perform(get("/api/materials/" + offSale))
                .andExpect(status().isNotFound());
    }

    @Test
    void cartRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("请先登录"));
        mockMvc.perform(post("/api/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cartAddListRemoveFlow() throws Exception {
        String token = registerBuyer("it_buyer_cart");
        long materialId = insertMaterial("购物车流程资料", 990, MaterialRecord.ON_SALE);

        mockMvc.perform(post("/api/cart").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialId\":" + materialId + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/cart").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialId\":" + materialId + "}"))
                .andExpect(status().isOk());
        assertThat(count("t_cart_item", "material_id = ?", materialId)).isEqualTo(1);

        mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("购物车流程资料"))
                .andExpect(jsonPath("$.items[0].onSale").value(true))
                .andExpect(jsonPath("$.items[0].purchased").value(false))
                .andExpect(jsonPath("$.totalCents").value(990));

        mockMvc.perform(delete("/api/cart/" + materialId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalCents").value(0));
    }

    @Test
    void cartRejectsPurchasedOffSaleAndMissing() throws Exception {
        String token = registerBuyer("it_buyer_reject");
        long userId = jdbc.queryForObject(
                "SELECT id FROM t_user WHERE username = 'it_buyer_reject'", Long.class);
        long purchased = insertMaterial("已购资料", 990, MaterialRecord.ON_SALE);
        long offSale = insertMaterial("下架资料", 590, MaterialRecord.OFF_SALE);

        entitlementCommand.grantManual(new EntitlementCommand.GrantCommand(
                userId, purchased, ProductTypes.MATERIAL, null, null, null, 1));

        mockMvc.perform(post("/api/cart").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialId\":" + purchased + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("您已购买该资料，无需重复购买"));

        mockMvc.perform(post("/api/cart").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialId\":" + offSale + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("资料已下架"));

        mockMvc.perform(post("/api/cart").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialId\":999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("资料不存在"));

        long inCart = insertMaterial("先加购后购买", 700, MaterialRecord.ON_SALE);
        mockMvc.perform(post("/api/cart").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialId\":" + inCart + "}"))
                .andExpect(status().isOk());
        entitlementCommand.grantManual(new EntitlementCommand.GrantCommand(
                userId, inCart, ProductTypes.MATERIAL, null, null, null, 1));
        mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].purchased").value(true))
                .andExpect(jsonPath("$.totalCents").value(0));
    }

    @Test
    void checkoutRequiresLoginAndNonEmptySelection() throws Exception {
        mockMvc.perform(post("/api/cart/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialIds\":[1],\"clientRequestId\":\"r\"}"))
                .andExpect(status().isUnauthorized());

        String token = registerBuyer("it_buyer_checkout_empty");
        mockMvc.perform(post("/api/cart/checkout").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialIds\":[],\"clientRequestId\":\"r\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkoutFlowCreatesMultiItemOrder() throws Exception {
        String token = registerBuyer("it_buyer_checkout");
        long m1 = insertMaterial("HTTP结算A", 990, MaterialRecord.ON_SALE);
        long m2 = insertMaterial("HTTP结算B", 1590, MaterialRecord.ON_SALE);
        for (long id : new long[]{m1, m2}) {
            mockMvc.perform(post("/api/cart").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"materialId\":" + id + "}"))
                    .andExpect(status().isOk());
        }

        MvcResult result = mockMvc.perform(post("/api/cart/checkout").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialIds\":[" + m1 + "," + m2 + "],\"clientRequestId\":\"http-checkout-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productType").value("MATERIAL"))
                .andExpect(jsonPath("$.productTitle").value("资料订单 共2件"))
                .andExpect(jsonPath("$.priceCents").value(2580))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn();
        String orderNo = objectMapper.readTree(result.getResponse().getContentAsString()).get("orderNo").asText();

        mockMvc.perform(get("/api/orders/" + orderNo).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].title").value("HTTP结算A"))
                .andExpect(jsonPath("$.order.productTitle").value("资料订单 共2件"));

        mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(post("/api/cart/checkout").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialIds\":[" + m1 + "],\"clientRequestId\":\"http-checkout-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNo").value(orderNo));
    }
}