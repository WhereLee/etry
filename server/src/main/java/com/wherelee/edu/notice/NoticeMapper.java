package com.wherelee.edu.notice;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NoticeMapper {

    String COLUMNS = "id, title, content, publisher_id, status, is_top, published_at, created_at, updated_at";

    @Select("SELECT " + COLUMNS + " FROM t_notice WHERE id = #{id}")
    Notice findById(@Param("id") Long id);

    @Insert("INSERT INTO t_notice (title, content, publisher_id, status, is_top, published_at) "
            + "VALUES (#{title}, #{content}, #{publisherId}, #{status}, #{isTop}, #{publishedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Notice notice);

    @Update("UPDATE t_notice SET title = #{title}, content = #{content}, status = #{status}, "
            + "is_top = #{isTop}, published_at = #{publishedAt} WHERE id = #{id}")
    int update(Notice notice);

    @Delete("DELETE FROM t_notice WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /** 前台列表、后台列表共用，动态条件在 XML */
    List<Notice> findPage(@Param("status") String status,
                          @Param("offset") int offset,
                          @Param("limit") int limit);

    long countPage(@Param("status") String status);
}
