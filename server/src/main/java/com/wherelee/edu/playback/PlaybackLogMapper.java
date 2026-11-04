package com.wherelee.edu.playback;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PlaybackLogMapper {

    int insertBatch(@Param("events") List<PlaybackLogEvent> events);

    List<PlaybackLogVO> findRecentByUser(@Param("userId") Long userId,
                                         @Param("eventType") String eventType,
                                         @Param("limit") int limit);
}