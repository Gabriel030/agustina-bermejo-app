package com.red_escuela.backend.evaluation.domain;

import com.red_escuela.backend.evaluation.entity.ClosureFinalCondition;

public enum ClosureSuggestedResult {
    PROMOTES,
    PROMOTION_CONDITIONAL,
    GOES_TO_FINAL,
    FAILED,
    FREE,
    PENDING;

    public String displayName() {
        return switch (this) {
            case PROMOTES -> "Promociona";
            case PROMOTION_CONDITIONAL -> "Promoción condicionada";
            case GOES_TO_FINAL -> "Va a final";
            case FAILED -> "Desaprobado";
            case FREE -> "Libre";
            case PENDING -> "Pendiente";
        };
    }

    public static ClosureSuggestedResult of(java.util.Optional<ClosureFinalCondition> condition) {
        return condition.map(value -> ClosureSuggestedResult.valueOf(value.name())).orElse(PENDING);
    }
}
