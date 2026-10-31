-- ============================================================
-- 课程课件资料
--
-- 挂在课程级而不是课时级：一门课的讲义、大纲、素材统一放一处，
-- 学员在课程详情页就能看到有哪些资料可下载。
--
-- 只存对象键，下载地址由服务端每次生成预签名 URL：
-- 桶是私有读，拿不到 key 就下载不了。
-- ============================================================

CREATE TABLE t_course_material
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    course_id  BIGINT       NOT NULL COMMENT '课程',
    title      VARCHAR(100) NOT NULL COMMENT '资料标题',
    file_name  VARCHAR(200) NOT NULL COMMENT '原始文件名，用于下载时命名',
    file_key   VARCHAR(255) NOT NULL COMMENT '对象键',
    size_bytes BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小，由服务端读取',
    sort       INT          NOT NULL DEFAULT 0 COMMENT '排序，小的在前',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_material_course (course_id, sort)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='课程课件资料';
