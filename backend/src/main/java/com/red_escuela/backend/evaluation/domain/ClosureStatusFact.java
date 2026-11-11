package com.red_escuela.backend.evaluation.domain;

import com.red_escuela.backend.evaluation.entity.ClosureStatus;

public enum ClosureStatusFact {
    CONFIRMED,
    REOPENED,
    CLOSED,
    NOT_DELIVERED;

    public static ClosureStatusFact of(ClosureStatus status) {
        return switch (status) {
            case TEACHER_CONFIRMED -> CONFIRMED;
            case REOPENED -> REOPENED;
            case CLOSED -> CLOSED;
            case DRAFT_AVAILABLE -> NOT_DELIVERED;
        };
    }
}
