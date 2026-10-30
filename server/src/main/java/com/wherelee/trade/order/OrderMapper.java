package com.wherelee.trade.order;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper {

    Order findById(@Param("id") Long id);

    Order findByUserAndIdempotencyKey(@Param("userId") Long userId,
                                      @Param("idempotencyKey") String idempotencyKey);

    Order findByOrderNoAndUserId(@Param("orderNo") String orderNo, @Param("userId") Long userId);

    int insert(Order order);

    List<Order> findPageByUser(@Param("userId") Long userId,
                               @Param("status") String status,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    long countByUser(@Param("userId") Long userId, @Param("status") String status);
}
