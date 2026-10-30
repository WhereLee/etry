package com.wherelee.etry.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /**
     * 原子预占配额：只有“已用 + 已预占 + 本次”不超配额时才成功，
     * 返回 0 表示空间不足。并发上传靠这条语句保证不会超卖。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update User u
               set u.reservedBytes = u.reservedBytes + :size
             where u.id = :id
               and u.usedBytes + u.reservedBytes + :size <= u.quotaBytes
            """)
    int reserveQuota(@Param("id") Long id, @Param("size") long size);

    /** 上传确认：预占转为已用 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update User u
               set u.usedBytes = u.usedBytes + :size,
                   u.reservedBytes = u.reservedBytes - :size
             where u.id = :id
               and u.reservedBytes >= :size
            """)
    int settleQuota(@Param("id") Long id, @Param("size") long size);

    /** 取消上传或清理过期记录：退回预占 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update User u
               set u.reservedBytes = u.reservedBytes - :size
             where u.id = :id
               and u.reservedBytes >= :size
            """)
    int releaseReserved(@Param("id") Long id, @Param("size") long size);

    /** 物理删除文件后释放已用配额 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update User u
               set u.usedBytes = u.usedBytes - :size
             where u.id = :id
               and u.usedBytes >= :size
            """)
    int releaseUsed(@Param("id") Long id, @Param("size") long size);

    /** 改密、重置密码时让旧 token 全部失效 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.tokenVersion = u.tokenVersion + 1 where u.id = :id")
    int bumpTokenVersion(@Param("id") Long id);

    /** 对账任务用：直接把已用字节改成重算结果 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.usedBytes = :used where u.id = :id")
    int updateUsedBytes(@Param("id") Long id, @Param("used") long used);

    /** 对账任务用：把预占清零（进程重启后残留的预占没有意义） */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.reservedBytes = 0 where u.id = :id")
    int clearReserved(@Param("id") Long id);
}
