package com.wherelee.trade.stock;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StockLogMapper {

    String COLUMNS = "id, product_id, order_id, type, quantity, created_at";

    @Insert("INSERT INTO t_stock_log (product_id, order_id, type, quantity) "
            + "VALUES (#{productId}, #{orderId}, #{type}, #{quantity})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(StockLog log);

    @Select("SELECT " + COLUMNS + " FROM t_stock_log WHERE order_id = #{orderId} ORDER BY id")
    List<StockLog> findByOrderId(@Param("orderId") Long orderId);
}
