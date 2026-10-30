package com.wherelee.trade.point;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PointAccountMapper {

    String COLUMNS = "id, user_id, balance, frozen, updated_at";

    @Select("SELECT " + COLUMNS + " FROM t_point_account WHERE user_id = #{userId}")
    PointAccount findByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO t_point_account (user_id, balance, frozen) VALUES (#{userId}, #{balance}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PointAccount account);

    /**
     * 冻结积分：可用减少、冻结增加。条件更新即 CAS，余额不足时返回 0。
     */
    int freeze(@Param("userId") Long userId, @Param("amount") long amount);
}
