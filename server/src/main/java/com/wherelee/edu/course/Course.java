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
    /** 难度：BEGINNER / INTERMEDIATE / ADVANCED，可为空（不填就不参与筛选） */
    private String level;
    /** 是否强制按章节顺序学：开课时要先把上一节学完 */
    private boolean sequential;
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

    /**
     * 能否物理删除。只有从未上架过的课能删：
     * published_at 有值说明它曾对学员暴露过，可能已经有人学过、评过、问过。
     * （等有订单之后，这条就是「已售课程不能删」的底层约束）
     */
    public boolean isDeletable() {
        return (STATUS_DRAFT.equals(status) || STATUS_REJECTED.equals(status)) && publishedAt == null;
    }
}
