package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.shared.exception.BadRequestException;

public class GradeItemOutsideConfiguredSlotsException extends BadRequestException {
    public GradeItemOutsideConfiguredSlotsException(GradeItemType type, Integer sequenceNumber, int configuredCount) {
        super("La evaluación " + type + " número " + sequenceNumber
                + " está fuera de las " + configuredCount + " configuradas para la comisión");
    }
}
