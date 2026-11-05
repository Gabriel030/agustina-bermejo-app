package com.red_escuela.backend.shared.pagination;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long totalElements) {

    public PageResponse {
        items = List.copyOf(items);
    }

    public static <T> PageResponse<T> of(List<T> all, int page, int size) {
        int from = (int) Math.min((long) page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new PageResponse<>(all.subList(from, to), page, size, all.size());
    }
}
