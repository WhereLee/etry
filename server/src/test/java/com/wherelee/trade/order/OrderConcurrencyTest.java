package com.wherelee.trade.order;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M0 最关键的验收：并发下不能超卖。
 * 用独立测试库 trade_center_test，跑前重置数据，避免影响开发库。
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderConcurrencyTest {

    /** 并发请求数 */
    private static final int CONCURRENCY = 100;
    /** USB-C 扩展坞，库存 10 */
    private static final long PRODUCT_ID = 5L;
    private static final int STOCK = 10;
    private static final long UNIT_POINTS = 2990L;
    private static final long INITIAL_POINTS = 50_000L;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("库存 10，100 个并发请求只能成功 10 单，且冻结库存与订单数一致")
    void concurrentOrdersShouldNotOversell() throws Exception {
        reset();
        String token = login();

        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(CONCURRENCY);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<String> unexpected = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < CONCURRENCY; i++) {
            final int index = i;
            pool.submit(() -> {
                try {
                    startGate.await();
                    JsonNode body = placeOrder(token, "conc-" + index);
                    if (body.path("code").asInt() == 0) {
                        success.incrementAndGet();
                    } else {
                        rejected.incrementAndGet();
                    }
                } catch (Exception e) {
                    unexpected.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    doneGate.countDown();
                }
            });
        }

        long begin = System.currentTimeMillis();
        startGate.countDown();
        boolean finished = doneGate.await(90, TimeUnit.SECONDS);
        long cost = System.currentTimeMillis() - begin;
        pool.shutdownNow();

        int frozen = queryInt("SELECT stock_frozen FROM t_product WHERE id = ?", PRODUCT_ID);
        int orderCount = queryInt("SELECT COUNT(*) FROM t_order WHERE idempotency_key LIKE 'conc-%'");
        long frozenPoints = queryLong("SELECT frozen FROM t_point_account WHERE user_id = 1");
        long balance = queryLong("SELECT balance FROM t_point_account WHERE user_id = 1");
        int stockLogCount = queryInt("SELECT COUNT(*) FROM t_stock_log");

        System.out.printf("并发结果: 成功=%d 拒绝=%d 库存冻结=%d 订单数=%d 积分冻结=%d 余额=%d 耗时=%dms%n",
                success.get(), rejected.get(), frozen, orderCount, frozenPoints, balance, cost);

        assertThat(finished).as("并发任务应在超时前完成").isTrue();
        assertThat(unexpected).as("不应出现非业务异常").isEmpty();
        assertThat(success.get()).as("成功下单数应等于库存").isEqualTo(STOCK);
        assertThat(rejected.get()).as("其余请求应被库存拦截").isEqualTo(CONCURRENCY - STOCK);
        assertThat(frozen).as("冻结库存应等于总库存").isEqualTo(STOCK);
        assertThat(orderCount).as("订单数应等于库存").isEqualTo(STOCK);
        assertThat(frozenPoints).as("冻结积分应等于成功单数 x 单价").isEqualTo(STOCK * UNIT_POINTS);
        assertThat(balance).as("可用余额应相应减少").isEqualTo(INITIAL_POINTS - STOCK * UNIT_POINTS);
        assertThat(stockLogCount).as("库存流水应每单一条").isEqualTo(STOCK);
    }

    private JsonNode placeOrder(String token, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Idempotency-Key", idempotencyKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"items\":[{\"productId\":" + PRODUCT_ID + ",\"quantity\":1}]}";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                url("/api/orders"), new HttpEntity<>(body, headers), JsonNode.class);
        return response.getBody();
    }

    private String login() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"username\":\"buyer\",\"password\":\"demo123\"}";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                url("/api/auth/login"), new HttpEntity<>(body, headers), JsonNode.class);
        JsonNode node = response.getBody();
        assertThat(node).isNotNull();
        return node.path("data").path("token").asText();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private void reset() {
        jdbcTemplate.update("DELETE FROM t_order_item");
        jdbcTemplate.update("DELETE FROM t_order");
        jdbcTemplate.update("DELETE FROM t_stock_log");
        jdbcTemplate.update("DELETE FROM t_point_flow WHERE type <> 'RECHARGE'");
        jdbcTemplate.update("UPDATE t_product SET stock_total = ?, stock_frozen = 0 WHERE id = ?", STOCK, PRODUCT_ID);
        jdbcTemplate.update("UPDATE t_point_account SET balance = ?, frozen = 0 WHERE user_id = 1", INITIAL_POINTS);
    }

    private int queryInt(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private long queryLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }
}
