package com.wherelee.trade.order;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderItemMapper {

    int batchInsert(@Param("items") List<OrderItem> items);

    List<OrderItem> findByOrderId(@Param("orderId") Long orderId);

    List<OrderItem> findByOrderIds(@Param("orderIds") List<Long> orderIds);
}
