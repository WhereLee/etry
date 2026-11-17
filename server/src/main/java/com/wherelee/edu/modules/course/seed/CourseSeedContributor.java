package com.wherelee.edu.modules.course.seed;

import com.wherelee.edu.modules.platform.seed.SeedContext;
import com.wherelee.edu.modules.platform.seed.SeedContributor;
import com.wherelee.edu.modules.platform.storage.DemoProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 课程模块种子：示例系列/章节/视频（t_series/t_series_chapter/t_series_video 归属 course），
 * 并把系列 id 注册进上下文供权益贡献者分配。
 */
@Component
public class CourseSeedContributor implements SeedContributor {

    private final DemoProperties demo;

    public CourseSeedContributor(DemoProperties demo) {
        this.demo = demo;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void contribute(SeedContext context) {
        if (count(context, "t_series") == 0) {
            Long teacherId = context.userId("teacher");
            Long physics1 = insertSeries(context, teacherId, "物理必修一", "高中物理必修一 · 录播课程", 1);
            Long c11 = insertChapter(context, physics1, "第一章 运动的描述", 1);
            insertVideo(context, c11, "1.1 质点 参考系", 1);
            insertVideo(context, c11, "1.2 时间 位移", 2);
            insertVideo(context, c11, "1.3 位置变化快慢的描述——速度", 3);
            Long c12 = insertChapter(context, physics1, "第二章 匀变速直线运动", 2);
            insertVideo(context, c12, "2.1 实验：探究小车速度随时间变化的规律", 1);
            insertVideo(context, c12, "2.2 匀变速直线运动的速度与时间的关系", 2);
            insertVideo(context, c12, "2.3 匀变速直线运动的位移与时间的关系", 3);

            Long physics2 = insertSeries(context, teacherId, "物理必修二", "高中物理必修二 · 录播课程", 2);
            Long c21 = insertChapter(context, physics2, "第一章 抛体运动", 1);
            insertVideo(context, c21, "1.1 曲线运动", 1);
            insertVideo(context, c21, "1.2 运动的合成与分解", 2);
            insertVideo(context, c21, "1.3 实验：探究平抛运动的特点", 3);
            Long c22 = insertChapter(context, physics2, "第二章 圆周运动", 2);
            insertVideo(context, c22, "2.1 圆周运动", 1);
            insertVideo(context, c22, "2.2 向心力", 2);
            insertVideo(context, c22, "2.3 向心加速度", 3);

            context.addSeriesId(physics1);
            context.addSeriesId(physics2);
        } else {
            context.addSeriesId(idByTitle(context, "物理必修一"));
            context.addSeriesId(idByTitle(context, "物理必修二"));
        }
    }

    private Long insertSeries(SeedContext context, Long teacherId, String title, String summary, int sort) {
        JdbcTemplate jdbc = context.jdbc();
        jdbc.update("""
                INSERT INTO t_series (teacher_id, title, summary, sort, status, published_at)
                VALUES (?, ?, ?, ?, 'PUBLISHED', CURRENT_TIMESTAMP(3))
                """, teacherId, title, summary, sort);
        return jdbc.queryForObject("SELECT id FROM t_series WHERE title = ?", Long.class, title);
    }

    private Long insertChapter(SeedContext context, Long seriesId, String title, int sort) {
        JdbcTemplate jdbc = context.jdbc();
        jdbc.update("""
                INSERT INTO t_series_chapter (series_id, title, sort)
                VALUES (?, ?, ?)
                """, seriesId, title, sort);
        return jdbc.queryForObject("""
                SELECT id FROM t_series_chapter WHERE series_id = ? AND title = ?
                """, Long.class, seriesId, title);
    }

    private void insertVideo(SeedContext context, Long chapterId, String title, int sort) {
        context.jdbc().update("""
                INSERT INTO t_series_video (chapter_id, title, sort, video_key, duration_seconds)
                VALUES (?, ?, ?, ?, ?)
                """, chapterId, title, sort, demo.videoKey(), demo.videoDurationSeconds());
    }

    private int count(SeedContext context, String table) {
        Integer value = context.jdbc().queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }

    private Long idByTitle(SeedContext context, String title) {
        return context.jdbc().queryForObject("SELECT id FROM t_series WHERE title = ?", Long.class, title);
    }
}