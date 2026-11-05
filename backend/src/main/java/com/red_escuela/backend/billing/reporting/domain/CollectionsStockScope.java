package com.red_escuela.backend.billing.reporting.domain;

import java.util.Objects;

public record CollectionsStockScope(Long programId, Long courseId) {

    public static final CollectionsStockScope TOTAL = new CollectionsStockScope(null, null);

    public CollectionsStockScope {
        if (programId != null && courseId != null) {
            throw new IllegalArgumentException("A stock scope is the whole institute, one program or one course");
        }
    }

    public static CollectionsStockScope ofProgram(Long programId) {
        return new CollectionsStockScope(Objects.requireNonNull(programId, "programId"), null);
    }

    public static CollectionsStockScope ofCourse(Long courseId) {
        return new CollectionsStockScope(null, Objects.requireNonNull(courseId, "courseId"));
    }

    public boolean isTotal() {
        return programId == null && courseId == null;
    }
}
