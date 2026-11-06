package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.shared.exception.BadRequestException;

public class GradeItemTypeNotPublishableException extends BadRequestException {
    public GradeItemTypeNotPublishableException(GradeItemType type) {
        super("El tipo de nota " + type + " no se publica por ítem");
    }
}
