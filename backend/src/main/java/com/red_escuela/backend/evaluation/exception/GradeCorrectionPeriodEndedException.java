package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

import java.time.LocalDate;

public class GradeCorrectionPeriodEndedException extends ConflictException {
    public GradeCorrectionPeriodEndedException(Long gradeItemId, LocalDate termEndDate) {
        super("La nota " + gradeItemId + " ya no se puede corregir: el cuatrimestre terminó el " + termEndDate);
    }
}
