-- ============================================================
-- 课程模块：课程 → 章节 → 课时
--
-- 结构说明：
--   一门课由若干章节组成，章节下面是课时（一个课时 = 一个视频）。
--   之所以分成三层，是因为教培的课通常按「第一章 基础、第二章 进阶」组织，
--   课时全部平铺到一个列表里，课一多后台就没法管了。
--
-- 归属说明：
--   teacher_id 指向 t_teacher_profile.id，不是 user_id。
--   课程属于「讲师身份」而不是「账号」——同一个人如果不是讲师，就不该有课。
--
-- 索引说明：
--   课程列表永远按 teacher + status 或 status + published_at 查，
--   所以这两组联合索引是必须的；subject_id 用于前台按学科筛选。
-- ============================================================

CREATE TABLE t_course
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    teacher_id    BIGINT       NOT NULL COMMENT '讲师档案 ID（t_teacher_profile.id）',
    subject_id    BIGINT       NULL COMMENT '学科，用于前台筛选',
    title         VARCHAR(100) NOT NULL COMMENT '课程标题',
    summary       VARCHAR(200) NULL COMMENT '一句话简介，列表页展示',
    description   TEXT         NULL COMMENT '详情描述',
    cover_key     VARCHAR(255) NULL COMMENT '封面对象键',
    status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT 草稿 / PENDING 待审 / PUBLISHED 已上架 / REJECTED 驳回 / OFFLINE 已下架',
    reject_reason VARCHAR(200) NULL COMMENT '驳回原因',
    published_at  DATETIME(3)  NULL COMMENT '首次上架时间',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_course_teacher (teacher_id, status),
    KEY idx_course_status (status, published_at),
    KEY idx_course_subject (subject_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='课程';

CREATE TABLE t_course_chapter
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    course_id  BIGINT       NOT NULL COMMENT '所属课程',
    title      VARCHAR(100) NOT NULL COMMENT '章节标题',
    sort       INT          NOT NULL DEFAULT 0 COMMENT '排序，小的在前',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_chapter_course (course_id, sort)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='课程章节';

CREATE TABLE t_course_lesson
(
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    course_id        BIGINT       NOT NULL COMMENT '冗余课程 ID：前台按课程查课时时少一次 join',
    chapter_id       BIGINT       NOT NULL COMMENT '所属章节',
    title            VARCHAR(100) NOT NULL COMMENT '课时标题',
    video_key        VARCHAR(255) NULL COMMENT '视频对象键，为空表示还没传视频',
    duration_seconds INT          NOT NULL DEFAULT 0 COMMENT '时长（秒），前端上传后回填',
    size_bytes       BIGINT       NOT NULL DEFAULT 0 COMMENT '文件字节数',
    sort             INT          NOT NULL DEFAULT 0 COMMENT '排序，小的在前',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_lesson_chapter (chapter_id, sort),
    KEY idx_lesson_course (course_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='课程课时';

-- 管理员审核课程需要单独的权限点，挂到 ADMIN 上。
-- 老师侧接口不挂权限点：能不能建课由「有没有通过审核的讲师档案」判断。
INSERT INTO t_permission (code, name, module)
VALUES ('course:review', '课程审核', 'course');

INSERT INTO t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM t_role r,
     t_permission p
WHERE r.code = 'ADMIN'
  AND p.code = 'course:review';
