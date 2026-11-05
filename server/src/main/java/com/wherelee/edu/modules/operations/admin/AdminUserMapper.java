package com.wherelee.edu.modules.operations.admin;

import com.wherelee.edu.modules.operations.admin.dto.AdminUserVO;
import com.wherelee.edu.modules.operations.admin.dto.SeriesOptionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminUserMapper {

    List<AdminUserVO> findPage(@Param("keyword") String keyword,
                               @Param("role") String role,
                               @Param("status") String status,
                               @Param("offset") int offset,
                               @Param("size") int size);

    long countUsers(@Param("keyword") String keyword,
                    @Param("role") String role,
                    @Param("status") String status);

    List<Long> findSeriesIds(@Param("userId") Long userId);

    void deleteUserSeries(@Param("userId") Long userId);

    void insertUserSeries(@Param("userId") Long userId,
                          @Param("seriesId") Long seriesId,
                          @Param("sort") int sort);

    List<SeriesOptionVO> findAllSeries();
}