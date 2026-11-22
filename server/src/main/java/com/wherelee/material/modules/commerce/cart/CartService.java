package com.wherelee.material.modules.commerce.cart;

import com.wherelee.material.modules.entitlement.api.EntitlementQuery;
import com.wherelee.material.modules.entitlement.api.ProductTypes;
import com.wherelee.material.modules.material.api.MaterialQuery;
import com.wherelee.material.modules.platform.common.BizException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 购物车应用服务：只依赖 material/entitlement 的 api 端口，不直读领域表。
 * 电子资料数量恒 1；已购/下架资料不可加购，结算总额只计可售未购行。
 */
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final MaterialQuery materialQuery;
    private final EntitlementQuery entitlementQuery;

    public CartService(CartRepository cartRepository, MaterialQuery materialQuery,
                       EntitlementQuery entitlementQuery) {
        this.cartRepository = cartRepository;
        this.materialQuery = materialQuery;
        this.entitlementQuery = entitlementQuery;
    }

    public CartDtos.CartView list(long userId) {
        List<CartItemRecord> rows = cartRepository.findByUser(userId);
        if (rows.isEmpty()) {
            return new CartDtos.CartView(List.of(), 0);
        }
        List<Long> materialIds = rows.stream().map(CartItemRecord::materialId).toList();
        Map<Long, MaterialQuery.MaterialFact> facts = materialQuery.findFacts(materialIds).stream()
                .collect(Collectors.toMap(MaterialQuery.MaterialFact::materialId, Function.identity()));
        List<CartDtos.ItemView> items = rows.stream()
                .map(row -> {
                    MaterialQuery.MaterialFact fact = facts.get(row.materialId());
                    if (fact == null) {
                        return new CartDtos.ItemView(row.materialId(), "资料已删除", null,
                                0, "CNY", null, false, false);
                    }
                    boolean purchased = entitlementQuery.hasActive(userId, fact.materialId(), ProductTypes.MATERIAL);
                    return new CartDtos.ItemView(fact.materialId(), fact.title(), fact.category(),
                            fact.priceCents(), fact.currency(), fact.coverKey(),
                            "ON_SALE".equals(fact.saleStatus()), purchased);
                })
                .toList();
        long total = items.stream()
                .filter(i -> i.onSale() && !i.purchased())
                .mapToLong(CartDtos.ItemView::priceCents)
                .sum();
        return new CartDtos.CartView(items, total);
    }

    public void add(long userId, long materialId) {
        MaterialQuery.MaterialFact fact = materialQuery.findFacts(List.of(materialId)).stream()
                .findFirst()
                .orElseThrow(() -> BizException.badRequest("资料不存在"));
        if (!"ON_SALE".equals(fact.saleStatus())) {
            throw BizException.badRequest("资料已下架");
        }
        if (entitlementQuery.hasActive(userId, materialId, ProductTypes.MATERIAL)) {
            throw BizException.badRequest("您已购买该资料，无需重复购买");
        }
        cartRepository.add(userId, materialId);
    }

    public void remove(long userId, long materialId) {
        cartRepository.remove(userId, materialId);
    }
}