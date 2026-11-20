package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;
import java.time.LocalDate;

public class HistoricalFinalDateBeforeEnrollmentException extends ConflictException implements ReportsErrorCode {
    public HistoricalFinalDateBeforeEnrollmentException(LocalDate finalDate, LocalDate enrollmentDate) {
        super("La fecha de la nota (" + finalDate + ") es anterior a la inscripción a la carrera (" + enrollmentDate + ")");
    }
}
