package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.time.LocalDateTime;

public class TermEnrollmentWindowOutsideTermException extends BadRequestException {
    public TermEnrollmentWindowOutsideTermException(LocalDateTime enrollmentEnd, LocalDateTime termEnd) {
        super("La inscripción no puede cerrar (" + enrollmentEnd + ") después de que termina el período (" + termEnd + ")");
    }
}
