package com.wherelee.material.integration;

import com.wherelee.material.integration.IntegrationTestSupport;
import com.wherelee.material.modules.commerce.cart.CartRepository;
import com.wherelee.material.modules.commerce.order.OrderItemRecord;
import com.wherelee.material.modules.commerce.order.OrderItemRepository;
import com.wherelee.material.modules.identity.rbac.RbacRepository;
import com.wherelee.material.modules.material.MaterialRecord;
import com.wherelee.material.modules.material.MaterialRepository;
import com.wherelee.material.modules.material.MaterialSellableCatalog;
import com.wherelee.material.modules.material.api.MaterialQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 阶段2 数据模型验证：资料目录/全文检索、可售门槛、购物车幂等与并发、
 * 订单明细快照与唯一约束、RBAC 预置角色与授权链路。
 */
class MaterialStoreCoreIntegrationTest extends IntegrationTestSupport {

    @Autowired private MaterialRepository materialRepository;
    @Autowired private MaterialSellableCatalog materialSellableCatalog;
    @Autowired private MaterialQuery materialQuery;
    @Autowired private CartRepository cartRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private RbacRepository rbacRepository;

    private long insertMaterial(String title, long priceCents, String saleStatus, String fileKey) {
        return materialRepository.insert(new MaterialRecord(
                null, title, "EXAM_PAPER", "SENIOR", "数学", "集成测试资料",
                priceCents, "CNY", 10, 3, null, fileKey, saleStatus,
                null, null, null));
    }

    @Test
    void materialInsertListAndFulltextSearch() {
        long id = insertMaterial("高中数学函数专项试卷", 990, MaterialRecord.ON_SALE, "material/it-1.pdf");
        insertMaterial("初中英语语法讲义", 590, MaterialRecord.ON_SALE, "material/it-2.pdf");
        insertMaterial("下架资料", 100, MaterialRecord.OFF_SALE, null);

        assertThat(materialRepository.findById(id)).isPresent();
        assertThat(materialRepository.listOnSale(null, 10, 0)).hasSize(2);
        assertThat(materialRepository.listOnSale("EXAM_PAPER", 10, 0)).hasSize(2);
        assertThat(materialRepository.search("函数", 10))
                .extracting(MaterialRecord::title)
                .containsExactly("高中数学函数专项试卷");
        assertThat(materialRepository.search("不存在的关键词xyz", 10)).isEmpty();
    }

    @Test
    void sellableCatalogRequiresOnSalePriceAndFile() {
        long offSale = insertMaterial("未上架", 990, MaterialRecord.OFF_SALE, "material/a.pdf");
        long noFile = insertMaterial("未挂文件", 990, MaterialRecord.ON_SALE, null);
        long ready = insertMaterial("可售资料", 990, MaterialRecord.ON_SALE, "material/b.pdf");

        assertThat(materialSellableCatalog.findPurchasable(offSale)).isEmpty();
        assertThat(materialSellableCatalog.findPurchasable(noFile)).isEmpty();
        assertThat(materialSellableCatalog.findPurchasable(ready)).isPresent()
                .get()
                .satisfies(p -> {
                    assertThat(p.productType()).isEqualTo("MATERIAL");
                    assertThat(p.title()).isEqualTo("可售资料");
                    assertThat(p.priceCents()).isEqualTo(990);
                });
    }

    @Test
    void materialQueryFacts() {
        long id = insertMaterial("事实查询", 990, MaterialRecord.ON_SALE, "material/c.pdf");
        List<MaterialQuery.MaterialFact> facts = materialQuery.findFacts(List.of(id, 999999L));
        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).title()).isEqualTo("事实查询");
        assertThat(materialQuery.findFacts(List.of())).isEmpty();
    }

    @Test
    void cartAddIsIdempotentAndConcurrencySafe() throws Exception {
        long materialId = insertMaterial("购物车资料", 990, MaterialRecord.ON_SALE, "material/d.pdf");
        long userId = fx.studentId();

        assertThat(cartRepository.add(userId, materialId)).isTrue();
        assertThat(cartRepository.add(userId, materialId)).isFalse();

        var results = runConcurrently(8, () -> cartRepository.add(userId, materialId));
        assertThat(results).allSatisfy(r -> assertThat(r.success()).isTrue());
        assertThat(count("t_cart_item", "user_id = ? AND material_id = ?", userId, materialId)).isEqualTo(1);

        assertThat(cartRepository.findByUser(userId)).hasSize(1);
        assertThat(cartRepository.exists(userId, materialId)).isTrue();
        assertThat(cartRepository.removeByUserAndMaterials(userId, List.of(materialId))).isEqualTo(1);
        assertThat(cartRepository.exists(userId, materialId)).isFalse();
    }

    @Test
    void orderItemSnapshotsAndUniqueConstraint() {
        var order = createOrder(nextRequestId());
        var orderRecord = orderRepository.findByOrderNo(order.orderNo()).orElseThrow();
        long m1 = insertMaterial("明细A", 990, MaterialRecord.ON_SALE, "material/e.pdf");
        long m2 = insertMaterial("明细B", 590, MaterialRecord.ON_SALE, "material/f.pdf");

        orderItemRepository.insertAll(orderRecord.id(), List.of(
                new OrderItemRecord(null, null, m1, "明细A快照", 990, null),
                new OrderItemRecord(null, null, m2, "明细B快照", 590, null)));

        var items = orderItemRepository.findByOrderId(orderRecord.id());
        assertThat(items).hasSize(2);
        assertThat(items.get(0).titleSnapshot()).isEqualTo("明细A快照");
        assertThat(items.get(0).priceCents()).isEqualTo(990);

        assertThatThrownBy(() -> orderItemRepository.insertAll(orderRecord.id(), List.of(
                new OrderItemRecord(null, null, m1, "重复行", 990, null))))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void rbacBuiltInRolesAndGrantChain() {
        assertThat(rbacRepository.listRoles())
                .extracting(r -> r.code())
                .containsExactly("BUYER", "OPERATOR", "CUSTOMER_SERVICE", "FINANCE", "SUPER_ADMIN");
        assertThat(rbacRepository.findRoleByCode("BUYER")).isPresent()
                .get().satisfies(r -> assertThat(r.dataScope()).isEqualTo("SELF"));

        long userId = fx.studentId();
        assertThat(rbacRepository.grantRole(userId, "BUYER", fx.adminId())).isTrue();
        assertThat(rbacRepository.grantRole(userId, "BUYER", fx.adminId())).isFalse();
        assertThat(rbacRepository.grantRole(userId, "NO_SUCH_ROLE", fx.adminId())).isFalse();
        assertThat(rbacRepository.findRoleCodesByUser(userId)).containsExactly("BUYER");

        rbacRepository.upsertPermission("order:read", "订单查询", "commerce");
        rbacRepository.upsertPermission("order:read", "订单查询改名", "commerce");
        assertThat(rbacRepository.linkRolePermission("BUYER", "order:read")).isTrue();
        assertThat(rbacRepository.linkRolePermission("BUYER", "order:read")).isFalse();
        assertThat(rbacRepository.linkRolePermission("BUYER", "no:such")).isFalse();
        assertThat(rbacRepository.findPermissionCodesByUser(userId)).containsExactly("order:read");

        assertThat(rbacRepository.revokeRole(userId, "BUYER")).isEqualTo(1);
        assertThat(rbacRepository.findRoleCodesByUser(userId)).isEmpty();
        assertThat(rbacRepository.findPermissionCodesByUser(userId)).isEmpty();
    }
}