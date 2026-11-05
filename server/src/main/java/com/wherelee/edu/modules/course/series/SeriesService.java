package com.wherelee.edu.modules.course.series;

import com.wherelee.edu.modules.platform.aop.Measured;
import com.wherelee.edu.modules.platform.aop.PlaybackAudit;
import com.wherelee.edu.modules.platform.common.BizException;
import com.wherelee.edu.modules.learning.api.PlaybackEventPublisher;
import com.wherelee.edu.modules.course.series.dto.SeriesDtos;
import com.wherelee.edu.modules.learning.api.PlaybackUrlProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SeriesService {

    private final SeriesRepository repository;
    private final PlaybackUrlProvider playbackUrlProvider;
    private final PlaybackEventPublisher playbackEventPublisher;

    public SeriesService(SeriesRepository repository,
                         PlaybackUrlProvider playbackUrlProvider,
                         PlaybackEventPublisher playbackEventPublisher) {
        this.repository = repository;
        this.playbackUrlProvider = playbackUrlProvider;
        this.playbackEventPublisher = playbackEventPublisher;
    }

    public List<SeriesDtos.SeriesSummary> listForStudent(Long userId) {
        return repository.findForStudent(userId);
    }

    public SeriesDtos.SeriesDetail detail(Long userId, Long seriesId) {
        SeriesRepository.SeriesHeader header = repository.findAssignedSeries(userId, seriesId)
                .orElseThrow(() -> BizException.notFound("系列不存在或未分配给你"));

        Map<Long, List<SeriesDtos.VideoItem>> videosByChapter = new LinkedHashMap<>();
        for (SeriesRepository.VideoRow video : repository.findVideos(seriesId, userId)) {
            videosByChapter.computeIfAbsent(video.chapterId(), key -> new ArrayList<>())
                    .add(new SeriesDtos.VideoItem(
                            video.id(), video.title(), video.durationSeconds(),
                            video.positionSeconds(), video.completed()));
        }

        List<SeriesDtos.ChapterItem> chapters = repository.findChapters(seriesId).stream()
                .map(chapter -> new SeriesDtos.ChapterItem(
                        chapter.id(),
                        chapter.title(),
                        videosByChapter.getOrDefault(chapter.id(), List.of())
                ))
                .toList();

        return new SeriesDtos.SeriesDetail(
                header.id(), header.title(), header.summary(), header.teacherName(), chapters);
    }

    @Measured("playback.play")
    @PlaybackAudit("playUrl")
    public SeriesDtos.PlayInfo play(Long userId, Long videoId, String userAgent) {
        SeriesDtos.PlayableVideo video = repository.findPlayableVideo(userId, videoId)
                .orElseThrow(() -> BizException.notFound("视频不存在或无权观看"));
        if (video.videoKey() == null || video.videoKey().isBlank()) {
            throw BizException.notFound("视频尚未配置");
        }
        PlaybackUrlProvider.PlaybackUrl cached = playbackUrlProvider.get(video.videoKey());
        playbackEventPublisher.recordUrlRequest(userId, video.id(), video.seriesId(), cached.cacheHit(), userAgent);
        return new SeriesDtos.PlayInfo(video.id(), video.title(), cached.url(), cached.expiresAt().getEpochSecond());
    }
}
