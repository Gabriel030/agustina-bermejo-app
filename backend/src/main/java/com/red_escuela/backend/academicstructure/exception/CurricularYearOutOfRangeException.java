package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class CurricularYearOutOfRangeException extends BadRequestException {
    public CurricularYearOutOfRangeException(Integer year, Integer totalYears) {
        super("El año curricular " + year + " está fuera de la duración de la carrera (" + totalYears + " años)");
    }
}
