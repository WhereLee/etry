package com.wherelee.edu.learning;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class LearningProgress {

    /** 播放到这个比例就算学完。留 10% 余量，避免片尾几秒没播到就不算完成 */
    public static final double COMPLETE_RATIO = 0.9;

    private Long id;
    private Long userId;
    private Long courseId;
    private Long lessonId;
    private Integer positionSeconds;
    private Integer durationSeconds;
    private Boolean completed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 是否算学完。时长为 0（老数据或未回填）时不算，避免除零和一提交就完成。
     */
    public static boolean isCompleted(int positionSeconds, int durationSeconds) {
        if (durationSeconds <= 0) {
            return false;
        }
        return positionSeconds >= durationSeconds * COMPLETE_RATIO;
    }
}
