package com.wherelee.etry.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "files")
@Getter
@Setter
public class FileObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 对象存储里的精确 key，由服务端生成 */
    @Column(name = "object_key", nullable = false, unique = true)
    private String objectKey;

    /** 展示名，可重复，改它不动 object key */
    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "content_type")
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "committed_at")
    private LocalDateTime committedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "trash_expires_at")
    private LocalDateTime trashExpiresAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
