-- 评价治理（业务规则 §五第 3 条，2026-11-01 拍板）：
-- 管理员可隐藏任意评价（软标记）。前台评价列表、评分均值、评价总数全部排除 hidden=1 的记录；
-- 记录本身保留，审计可对账；学员更新自己的评价时 hidden 重置（内容变了重新可见）。
ALTER TABLE t_course_review
    ADD COLUMN hidden TINYINT NOT NULL DEFAULT 0 COMMENT '1=被管理员隐藏，前台不可见' AFTER content;
