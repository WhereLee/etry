package com.wherelee.material.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherelee.material.modules.commerce.api.OrderDtos;
import com.wherelee.material.modules.commerce.order.OrderItemRecord;
import com.wherelee.material.modules.commerce.order.OrderItemRepository;
import com.wherelee.material.modules.commerce.order.OrderRepository;
import com.wherelee.material.modules.entitlement.api.EntitlementCommand;
import com.wherelee.material.modules.entitlement.api.ProductTypes;
import com.wherelee.material.modules.material.MaterialRecord;
import com.wherelee.material.modules.material.MaterialRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单列表 owned 批量计算回归：购物车多商品订单与单商品订单在一次查询内判定权益。
 */
@AutoConfigureMockMvc
class OrderListOwnedIntegrationTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private EntitlementCommand entitlementCommand;

    @Test
    @DisplayName("订单列表一次查询正确计算购物车订单与单商品订单的 owned")
    void listComputesOwnedInOnePass() throws Exception {
        String token = register("it_order_list_buyer");
        long buyerId = userId("it_order_list_buyer");

        long m1 = insertMaterial("IT 资料一");
        long m2 = insertMaterial("IT 资料二");
        long m3 = insertMaterial("IT 资料三");

        long paidCartId = orderRepository.insertCartOrder("IT-LIST-CART-PAID", buyerId,
                "资料订单 共2件", 1980, "CNY", "it-list-1", LocalDateTime.now().plusMinutes(30));
        orderItemRepository.insertAll(paidCartId, List.of(
                new OrderItemRecord(null, null, m1, "IT 资料一", 990, null),
                new OrderItemRecord(null, null, m2, "IT 资料二", 990, null)));
        grant(buyerId, m1, ProductTypes.MATERIAL, paidCartId);
        grant(buyerId, m2, ProductTypes.MATERIAL, paidCartId);

        long unpaidCartId = orderRepository.insertCartOrder("IT-LIST-CART-UNPAID", buyerId,
                "资料订单 共1件", 990, "CNY", "it-list-2", LocalDateTime.now().plusMinutes(30));
        orderItemRepository.insertAll(unpaidCartId, List.of(
                new OrderItemRecord(null, null, m3, "IT 资料三", 990, null)));

        OrderDtos.OrderView seriesOrder = orderService.create(buyerId,
                new OrderDtos.CreateOrderRequest(fx.seriesId(), "it-list-3"));
        long seriesOrderId = orderRepository.findByOrderNo(seriesOrder.orderNo()).orElseThrow().id();
        grant(buyerId, fx.seriesId(), ProductTypes.COURSE_SERIES, seriesOrderId);

        MvcResult result = mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = objectMapper.readTree(result.getResponse().getContentAsString()).get("records");
        Map<String, Boolean> ownedByOrder = new HashMap<>();
        records.forEach(node -> ownedByOrder.put(node.get("orderNo").asText(), node.get("owned").asBoolean()));

        assertThat(ownedByOrder).hasSize(3);
        assertThat(ownedByOrder.get("IT-LIST-CART-PAID")).isTrue();
        assertThat(ownedByOrder.get("IT-LIST-CART-UNPAID")).isFalse();
        assertThat(ownedByOrder.get(seriesOrder.orderNo())).isTrue();
    }

    private void grant(long userId, long productId, String productType, long orderId) {
        entitlementCommand.grant(new EntitlementCommand.GrantCommand(
                userId, productId, productType, orderId, null, null, 0));
    }

    private long insertMaterial(String title) {
        return materialRepository.insert(new MaterialRecord(null, title, "EXAM_PAPER", "SENIOR", "数学",
                "集成测试", 990, "CNY", 10, 3, null, "it/list.pdf",
                MaterialRecord.ON_SALE, null, null, null, false));
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