package com.wherelee.edu.learning;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 学习进度的判定策略。
 *
 * 严格模式打开后，完成判定从「位置到 90%」变成「位置到 90% 且真实时间也过了 90% 的课时时长」：
 * 一次请求直接把位置报到底的，第一次上报时间就是此刻，时间条件永远不成立。
 *
 * 关着的时候和以前一样，只看位置——免费课没有证书和积分，刷进度的收益本来就低，
 * 开着反而要前端配合心跳。
 */
@ConfigurationProperties(prefix = "app.learning")
public record LearningProperties(boolean strictMode) {
}
