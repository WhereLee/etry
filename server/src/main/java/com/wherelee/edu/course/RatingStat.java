package com.wherelee.edu.course;

/** 课程评分汇总：均分和评价条数。没有评价时 avg 为 0、total 为 0 */
public record RatingStat(double avgRating, long total) {
}
