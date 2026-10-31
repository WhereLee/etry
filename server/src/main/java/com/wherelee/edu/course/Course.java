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

    /**
     * 可编辑：草稿、被驳回、已下架。
     *
     * 上架中不允许直接改，避免审核通过后内容被换掉；
     * 想改就先下架——下架是收回自己的内容，不影响任何人，所以老师自己就能做。
     */
    public boolean isEditable() {
        return STATUS_DRAFT.equals(status)
                || STATUS_REJECTED.equals(status)
                || STATUS_OFFLINE.equals(status);
    }

    /** 改完要重新走审核，所以可编辑就意味着可提交 */
    public boolean isSubmittable() {
        return isEditable();
    }
}
