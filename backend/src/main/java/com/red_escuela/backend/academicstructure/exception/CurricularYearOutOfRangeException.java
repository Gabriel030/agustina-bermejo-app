package com.red_escuela.backend.academicstructure.exception;

public class CurricularYearOutOfRangeException extends RuntimeException {
    public CurricularYearOutOfRangeException(Integer year, Integer totalYears) {
        super("El año curricular " + year + " está fuera de la duración de la carrera (" + totalYears + " años)");
    }
}
