package com.red_escuela.backend.shared.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class InClauseChunks {
    public static final int DEFAULT_SIZE = 1000;

    private InClauseChunks() {
    }

    public static <T> List<List<T>> of(Collection<T> values) {
        return of(values, DEFAULT_SIZE);
    }

    public static <T> List<List<T>> of(Collection<T> values, int size) {
        List<T> all = new ArrayList<>(values);
        List<List<T>> chunks = new ArrayList<>();
        for (int from = 0; from < all.size(); from += size) {
            chunks.add(all.subList(from, Math.min(from + size, all.size())));
        }
        return chunks;
    }
}
