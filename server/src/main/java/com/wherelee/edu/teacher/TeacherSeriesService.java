package com.wherelee.edu.teacher;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.series.dto.SeriesDtos;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TeacherSeriesService {

    private final TeacherContentRepository repository;

    public TeacherSeriesService(TeacherContentRepository repository) {
        this.repository = repository;
    }

    public List<SeriesDtos.TeacherSeriesSummary> list(Long teacherId) {
        return repository.findSeries(teacherId);
    }

    public SeriesDtos.TeacherSeriesDetail detail(Long teacherId, Long seriesId) {
        TeacherContentRepository.SeriesHeader header = repository.findSeriesHeader(teacherId, seriesId)
                .orElseThrow(() -> BizException.notFound("系列不存在"));

        Map<Long, List<SeriesDtos.TeacherVideoItem>> videosByChapter = new LinkedHashMap<>();
        for (TeacherContentRepository.VideoRow video : repository.findVideos(seriesId)) {
            videosByChapter.computeIfAbsent(video.chapterId(), key -> new ArrayList<>())
                    .add(new SeriesDtos.TeacherVideoItem(
                            video.id(), video.title(), video.sort(), video.videoKey(),
                            video.mediaStatus(), video.durationSeconds()));
        }

        List<SeriesDtos.TeacherChapterItem> chapters = repository.findChapters(seriesId).stream()
                .map(chapter -> new SeriesDtos.TeacherChapterItem(
                        chapter.id(),
                        chapter.title(),
                        chapter.sort(),
                        videosByChapter.getOrDefault(chapter.id(), List.of())
                ))
                .toList();

        return new SeriesDtos.TeacherSeriesDetail(
                header.id(), header.title(), header.summary(), header.status(),
                header.sort(), header.teacherName(), chapters);
    }
}
