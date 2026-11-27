package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidCurriculumPlanDatesException extends BadRequestException {
    public InvalidCurriculumPlanDatesException(String detail) {
        super("Fechas de plan de estudios inválidas: " + detail);
    }
}
