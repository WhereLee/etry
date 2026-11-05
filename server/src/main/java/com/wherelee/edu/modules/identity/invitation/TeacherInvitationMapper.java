package com.wherelee.edu.modules.identity.invitation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TeacherInvitationMapper {

    int insert(TeacherInvitation invitation);

    TeacherInvitation findById(@Param("id") Long id);

    TeacherInvitation findByTokenHash(@Param("tokenHash") String tokenHash);

    TeacherInvitation findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    TeacherInvitation findPendingByUsername(@Param("username") String username,
                                            @Param("now") LocalDateTime now);

    List<TeacherInvitationVO> findPage(@Param("status") String status,
                                       @Param("pageNow") LocalDateTime pageNow,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    long count(@Param("status") String status);

    int markUsed(@Param("id") Long id,
                 @Param("userId") Long userId,
                 @Param("now") LocalDateTime now);

    int revoke(@Param("id") Long id, @Param("now") LocalDateTime now);
}
