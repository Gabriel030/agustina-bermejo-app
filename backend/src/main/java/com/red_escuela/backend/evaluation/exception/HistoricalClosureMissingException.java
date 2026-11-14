package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class HistoricalClosureMissingException extends ConflictException implements ReportsErrorCode {
    public HistoricalClosureMissingException(Long sectionId) {
        super("La comisión " + sectionId + " no tiene acta de cierre histórica");
    }
}
