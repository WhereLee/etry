package com.wherelee.edu.course;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Course {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_OFFLINE = "OFFLINE";

    private Long id;
    /** 讲师档案 ID，不是 user_id：课程属于讲师身份，不属于账号 */
    private Long teacherId;
    private Long subjectId;
    private String title;
    private String summary;
    private String description;
    private String coverKey;
    private String status;
    private String rejectReason;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 只有草稿和被驳回的能改内容；待审和已上架的不允许改，避免审核中内容被换掉 */
    public boolean isEditable() {
        return STATUS_DRAFT.equals(status) || STATUS_REJECTED.equals(status);
    }

    public boolean isSubmittable() {
        return STATUS_DRAFT.equals(status) || STATUS_REJECTED.equals(status);
    }
}
