package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.evaluation.entity.ClosureStatus;

public enum PendingClosureState {
    NOT_STARTED,
    DRAFT_AVAILABLE,
    TEACHER_CONFIRMED,
    REOPENED;

    public static PendingClosureState from(ClosureStatus status) {
        return status == null ? NOT_STARTED : valueOf(status.name());
    }
}
