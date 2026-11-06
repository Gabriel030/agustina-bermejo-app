package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.shared.exception.ConflictException;

public class GradeItemPublishedLockedException extends ConflictException {
    public GradeItemPublishedLockedException(GradeItemType type, Integer sequenceNumber) {
        super("La nota " + type + " " + sequenceNumber + " ya fue publicada y no admite cambios");
    }
}
