package com.wherelee.trade.order;

import com.wherelee.trade.common.BizException;
import com.wherelee.trade.common.ErrorCode;
import com.wherelee.trade.order.dto.CreateOrderRequest;
import com.wherelee.trade.order.dto.OrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 下单主流程的边界与幂等，直接调服务层，不经过 HTTP。
 * 使用独立测试库，每个用例前重置数据。
 */
@ActiveProfiles("test")
@SpringBootTest
class OrderServiceTest {

    /** 桌面收纳盒：100 积分，库存 200 */
    private static final long PRODUCT_ID = 6L;
    private static final long UNIT_POINTS = 100L;
    private static final long USER_ID = 1L;
    private static final long INITIAL_BALANCE = 50_000L;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reset() {
        jdbcTemplate.update("DELETE FROM t_order_item");
        jdbcTemplate.update("DELETE FROM t_order");
        jdbcTemplate.update("DELETE FROM t_stock_log");
        jdbcTemplate.update("DELETE FROM t_point_flow WHERE type <> 'RECHARGE'");
        jdbcTemplate.update(
                "UPDATE t_product SET name = '桌面收纳盒', price_points = ?, stock_total = 200, stock_frozen = 0 WHERE id = ?",
                UNIT_POINTS, PRODUCT_ID);
        jdbcTemplate.update("UPDATE t_point_account SET balance = ?, frozen = 0 WHERE user_id = ?",
                INITIAL_BALANCE, USER_ID);
    }

    @Test
    @DisplayName("库存不足时抛业务异常，且不产生任何订单或冻结")
    void shouldRejectWhenStockNotEnough() {
        jdbcTemplate.update("UPDATE t_product SET stock_total = 1 WHERE id = ?", PRODUCT_ID);

        assertThatThrownBy(() -> orderService.create(USER_ID, "t-stock-1", request(2)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.STOCK_NOT_ENOUGH);

        assertThat(count("SELECT COUNT(*) FROM t_order")).isZero();
        assertThat(count("SELECT COUNT(*) FROM t_stock_log")).isZero();
        assertThat(queryLong("SELECT stock_frozen FROM t_product WHERE id = ?", PRODUCT_ID)).isZero();
        assertThat(queryLong("SELECT frozen FROM t_point_account WHERE user_id = ?", USER_ID)).isZero();
        assertThat(queryLong("SELECT balance FROM t_point_account WHERE user_id = ?", USER_ID))
                .isEqualTo(INITIAL_BALANCE);
    }

    @Test
    @DisplayName("积分不足时抛业务异常，且库存冻结被回滚")
    void shouldRejectWhenPointsNotEnough() {
        jdbcTemplate.update("UPDATE t_point_account SET balance = 50 WHERE user_id = ?", USER_ID);

        assertThatThrownBy(() -> orderService.create(USER_ID, "t-point-1", request(1)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.POINT_NOT_ENOUGH);

        assertThat(count("SELECT COUNT(*) FROM t_order")).isZero();
        assertThat(queryLong("SELECT stock_frozen FROM t_product WHERE id = ?", PRODUCT_ID)).isZero();
    }

    @Test
    @DisplayName("同一幂等键提交三次只产生一张订单")
    void shouldBeIdempotentWithSameKey() {
        OrderVO first = orderService.create(USER_ID, "t-idem-1", request(1));
        OrderVO second = orderService.create(USER_ID, "t-idem-1", request(1));
        OrderVO third = orderService.create(USER_ID, "t-idem-1", request(1));

        assertThat(second.orderNo()).isEqualTo(first.orderNo());
        assertThat(third.orderNo()).isEqualTo(first.orderNo());
        assertThat(count("SELECT COUNT(*) FROM t_order")).isEqualTo(1);
        assertThat(queryLong("SELECT stock_frozen FROM t_product WHERE id = ?", PRODUCT_ID)).isEqualTo(1);
        assertThat(queryLong("SELECT frozen FROM t_point_account WHERE user_id = ?", USER_ID)).isEqualTo(UNIT_POINTS);
    }

    @Test
    @DisplayName("订单明细保存下单时的商品快照，商品改价后不受影响")
    void shouldKeepSnapshotAfterProductPriceChanged() {
        OrderVO order = orderService.create(USER_ID, "t-snapshot-1", request(2));
        long originalTotal = order.totalPoints();

        jdbcTemplate.update("UPDATE t_product SET name = '改名后的商品', price_points = 9999 WHERE id = ?", PRODUCT_ID);

        Map<String, Object> item = jdbcTemplate.queryForMap(
                "SELECT product_name, unit_points, subtotal_points FROM t_order_item WHERE order_id = ?", order.id());

        assertThat(item.get("product_name")).isEqualTo("桌面收纳盒");
        assertThat(((Number) item.get("unit_points")).longValue()).isEqualTo(UNIT_POINTS);
        assertThat(((Number) item.get("subtotal_points")).longValue()).isEqualTo(originalTotal);
        assertThat(originalTotal).isEqualTo(UNIT_POINTS * 2);
    }

    @Test
    @DisplayName("订单有效期等于创建时间加 10 分钟")
    void shouldExpireInTenMinutes() {
        OrderVO order = orderService.create(USER_ID, "t-expire-1", request(1));

        LocalDateTime createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM t_order WHERE id = ?", LocalDateTime.class, order.id());
        LocalDateTime expireAt = jdbcTemplate.queryForObject(
                "SELECT expire_at FROM t_order WHERE id = ?", LocalDateTime.class, order.id());

        assertThat(createdAt).isNotNull();
        // 上半段用 Java 时间、下半段是数据库时间，两者相差几毫秒，
        // 所以用秒级区间判定而不是 toMinutes()（它会向下取整成 9）
        long seconds = Duration.between(createdAt, expireAt).getSeconds();
        assertThat(seconds).isBetween(590L, 600L);
    }

    private CreateOrderRequest request(int quantity) {
        return new CreateOrderRequest(
                List.of(new CreateOrderRequest.Item(PRODUCT_ID, quantity)),
                "hangzhou xihu");
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private long queryLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }
}
