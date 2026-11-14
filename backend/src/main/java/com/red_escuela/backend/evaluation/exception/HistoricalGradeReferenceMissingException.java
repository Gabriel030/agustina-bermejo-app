package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class HistoricalGradeReferenceMissingException extends ConflictException implements ReportsErrorCode {
    public HistoricalGradeReferenceMissingException(Long sectionId) {
        super("El alumno no tiene cursada importada en la comisión " + sectionId);
    }
}
