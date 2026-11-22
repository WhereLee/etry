package com.wherelee.material.modules.learning.api;

import java.util.Optional;

/**
 * 课程内容查询端口（learning 消费、course 实现）。learning 取视频元数据经此端口，
 * 不再直接 join 课程内容表（t_series_video/t_series_chapter）。
 */
public interface CourseContentQuery {

    Optional<VideoRef> findVideo(Long videoId);

    record VideoRef(Long videoId, Long seriesId, int durationSeconds) {
    }
}