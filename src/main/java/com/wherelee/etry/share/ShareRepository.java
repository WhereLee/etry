package com.wherelee.etry.share;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShareRepository extends JpaRepository<Share, Long> {

    Optional<Share> findByToken(String token);

    Optional<Share> findByIdAndUserId(Long id, Long userId);

    List<Share> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Share> findByFileId(Long fileId);

    /** 文件进回收站时，把它的分享一次性作废 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Share s set s.revokedAt = :now where s.fileId = :fileId and s.revokedAt is null")
    int revokeByFileId(@Param("fileId") Long fileId, @Param("now") LocalDateTime now);

    void deleteByFileId(Long fileId);
}
