package com.wherelee.edu.modules.operations.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminOrderMapper {

    List<AdminOrderVO> findPage(@Param("keyword") String keyword,
                                @Param("status") String status,
                                @Param("offset") int offset,
                                @Param("size") int size);

    long count(@Param("keyword") String keyword, @Param("status") String status);

    AdminOrderVO findOne(@Param("orderNo") String orderNo);

    List<AdminOrderVO> findExceptions(@Param("offset") int offset, @Param("size") int size);

    long countExceptions();
}
