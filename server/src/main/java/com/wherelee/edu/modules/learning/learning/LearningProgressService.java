package com.wherelee.edu.modules.learning.learning;

import com.wherelee.edu.modules.platform.common.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningProgressService {

    private final LearningProgressRepository repository;

    public LearningProgressService(LearningProgressRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void update(Long userId, Long videoId, LearningProgressDtos.UpdateRequest request) {
        LearningProgressRepository.AccessibleVideo video = repository.findAccessibleVideo(userId, videoId)
                .orElseThrow(() -> BizException.notFound("视频不存在或无权观看"));
        int duration = request.durationSeconds() > 0
                ? request.durationSeconds() : video.durationSeconds();
        int position = Math.max(0, request.positionSeconds());
        if (duration > 0) {
            position = Math.min(position, duration);
        }
        boolean completed = request.completed()
                || (duration > 0 && position >= Math.max(duration - 3, duration * 0.95));
        if (completed && duration > 0) {
            position = duration;
        }
        repository.upsert(userId, video.seriesId(), videoId, position, duration, completed);
    }
}
