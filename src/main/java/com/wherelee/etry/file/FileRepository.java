package com.wherelee.etry.file;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<FileObject, Long> {

    Optional<FileObject> findByIdAndUserId(Long id, Long userId);

    Optional<FileObject> findByObjectKey(String objectKey);

    Page<FileObject> findByUserIdAndStatusAndDeletedAtIsNull(Long userId, FileStatus status, Pageable pageable);

    Page<FileObject> findByUserIdAndStatusAndDeletedAtIsNullAndOriginalNameContaining(
            Long userId, FileStatus status, String keyword, Pageable pageable);

    Page<FileObject> findByUserIdAndDeletedAtIsNotNull(Long userId, Pageable pageable);

    List<FileObject> findByUserIdAndStatusAndDeletedAtIsNull(Long userId, FileStatus status);

    List<FileObject> findByStatusAndCreatedAtBefore(FileStatus status, LocalDateTime time);

    List<FileObject> findByDeletedAtIsNotNullAndTrashExpiresAtBefore(LocalDateTime time);

    List<FileObject> findByUserId(Long userId);

    /** 状态抢占：只有当前状态匹配才会更新，用来保证清理任务不会被重复执行 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update FileObject f set f.status = :to where f.id = :id and f.status = :from")
    int transitionStatus(@Param("id") Long id, @Param("from") FileStatus from, @Param("to") FileStatus to);
}
