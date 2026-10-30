package com.wherelee.trade.point;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PointFlowMapper {

    String COLUMNS = "id, user_id, order_id, type, amount, balance_after, created_at";

    @Insert("INSERT INTO t_point_flow (user_id, order_id, type, amount, balance_after) "
            + "VALUES (#{userId}, #{orderId}, #{type}, #{amount}, #{balanceAfter})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PointFlow flow);

    @Select("SELECT " + COLUMNS + " FROM t_point_flow WHERE user_id = #{userId} "
            + "ORDER BY id DESC LIMIT #{limit}")
    List<PointFlow> findRecent(@Param("userId") Long userId, @Param("limit") int limit);
}
