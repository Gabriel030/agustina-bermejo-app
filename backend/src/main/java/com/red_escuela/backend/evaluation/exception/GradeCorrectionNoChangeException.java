package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class GradeCorrectionNoChangeException extends ConflictException {
    public GradeCorrectionNoChangeException(Long gradeItemId) {
        super("La nota " + gradeItemId + " ya tiene ese valor");
    }
}
