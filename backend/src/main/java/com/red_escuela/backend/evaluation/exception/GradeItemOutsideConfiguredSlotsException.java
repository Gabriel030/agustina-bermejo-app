package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class GradeItemOutsideConfiguredSlotsException extends BadRequestException {
    public GradeItemOutsideConfiguredSlotsException(String typeName, Integer sequenceNumber, int configuredCount) {
        super("La evaluación " + typeName + " número " + sequenceNumber
                + " está fuera de las " + configuredCount + " configuradas para la comisión");
    }
}
