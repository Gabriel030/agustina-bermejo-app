package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class HistoricalEnrollmentReferenceMissingException extends ConflictException implements ReportsErrorCode {
    public HistoricalEnrollmentReferenceMissingException(String reference) {
        super("No existe en el sistema: " + reference);
    }
}
