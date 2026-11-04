package com.wherelee.edu.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminSeriesMapper {

    List<AdminSeriesVO> findPage(@Param("keyword") String keyword,
                                 @Param("status") String status,
                                 @Param("teacherId") Long teacherId,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    long count(@Param("keyword") String keyword,
               @Param("status") String status,
               @Param("teacherId") Long teacherId);

    AdminSeriesHeaderVO findHeader(@Param("id") Long id);

    List<AdminSeriesChapterVO> findChapters(@Param("seriesId") Long seriesId);

    List<AdminSeriesVideoVO> findVideos(@Param("seriesId") Long seriesId);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int updateSale(@Param("id") Long id,
                   @Param("saleStatus") String saleStatus,
                   @Param("priceCents") long priceCents);
}
