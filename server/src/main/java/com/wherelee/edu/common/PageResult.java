package com.wherelee.edu.common;

import java.util.List;

public record PageResult<T>(List<T> records, long total, int page, int size) {

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), 0, page, size);
    }

    public static <T> PageResult<T> of(List<T> records, long total, int page, int size) {
        return new PageResult<>(records, total, page, size);
    }
}
