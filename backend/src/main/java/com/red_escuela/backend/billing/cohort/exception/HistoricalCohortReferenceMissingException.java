package com.red_escuela.backend.billing.cohort.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class HistoricalCohortReferenceMissingException extends ConflictException implements ReportsErrorCode {
    public HistoricalCohortReferenceMissingException(String reference) {
        super("No existe en el sistema: " + reference);
    }
}
