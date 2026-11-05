package com.red_escuela.backend.shared.pagination;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long totalElements) {

    public static final int MAX_SIZE = 100;

    public PageResponse {
        items = List.copyOf(items);
    }

    public static <T> PageResponse<T> of(List<T> all, int page, int size) {
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), MAX_SIZE);
        int from = (int) Math.min((long) boundedPage * boundedSize, all.size());
        int to = Math.min(from + boundedSize, all.size());
        return new PageResponse<>(all.subList(from, to), boundedPage, boundedSize, all.size());
    }
}
