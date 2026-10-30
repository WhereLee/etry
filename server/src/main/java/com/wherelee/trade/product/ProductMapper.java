package com.wherelee.trade.product;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ProductMapper {

    List<Product> findPage(@Param("category") String category,
                           @Param("offset") int offset,
                           @Param("limit") int limit);

    long countPage(@Param("category") String category);

    Product findById(@Param("id") Long id);

    List<Product> findByIds(@Param("ids") Collection<Long> ids);

    /**
     * 预占库存：条件更新即 CAS，只有可售数量足够时才累加冻结。
     * 返回 0 表示库存不足或商品已下架。
     */
    int reserveStock(@Param("id") Long id, @Param("quantity") int quantity);
}
