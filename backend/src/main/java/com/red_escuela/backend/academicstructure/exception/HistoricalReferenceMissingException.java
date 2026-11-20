package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class HistoricalReferenceMissingException extends ConflictException implements ReportsErrorCode {
    public HistoricalReferenceMissingException(String reference) {
        super("No existe en el sistema: " + reference);
    }
}
