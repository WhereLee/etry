package com.wherelee.edu.message;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MessageMapper {

    String COLUMNS = "id, receiver_id, sender_id, title, content, is_read, read_at, created_at";

    @Select("SELECT " + COLUMNS + " FROM t_message WHERE id = #{id}")
    Message findById(@Param("id") Long id);

    @Insert("INSERT INTO t_message (receiver_id, sender_id, title, content) "
            + "VALUES (#{receiverId}, #{senderId}, #{title}, #{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Message message);

    /** 群发：一条 INSERT 写多行，不逐条往返 */
    int batchInsert(@Param("messages") List<Message> messages);

    /** 收件箱：receiver 等值 + 已读状态 + 时间倒序，与 idx_msg_receiver_read_time 对齐 */
    List<Message> findPage(@Param("receiverId") Long receiverId,
                           @Param("isRead") Boolean isRead,
                           @Param("offset") int offset,
                           @Param("limit") int limit);

    long countPage(@Param("receiverId") Long receiverId, @Param("isRead") Boolean isRead);

    @Select("SELECT COUNT(*) FROM t_message WHERE receiver_id = #{receiverId} AND is_read = 0")
    long countUnread(@Param("receiverId") Long receiverId);

    /** 标记已读时带上 receiver_id，防止改别人的消息 */
    @Update("UPDATE t_message SET is_read = 1, read_at = NOW(3) "
            + "WHERE id = #{id} AND receiver_id = #{receiverId} AND is_read = 0")
    int markRead(@Param("id") Long id, @Param("receiverId") Long receiverId);

    @Update("UPDATE t_message SET is_read = 1, read_at = NOW(3) "
            + "WHERE receiver_id = #{receiverId} AND is_read = 0")
    int markAllRead(@Param("receiverId") Long receiverId);
}
