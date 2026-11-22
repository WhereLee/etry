package com.wherelee.material.modules.material.api;

import java.util.Collection;
import java.util.List;

/**
 * 资料目录跨模块读端口：购物车/订单读模型按 id 批量取展示事实，不直读 t_material。
 */
public interface MaterialQuery {

    List<MaterialFact> findFacts(Collection<Long> materialIds);

    record MaterialFact(
            Long materialId,
            String title,
            String category,
            long priceCents,
            String currency,
            String saleStatus,
            String coverKey
    ) {
    }
}