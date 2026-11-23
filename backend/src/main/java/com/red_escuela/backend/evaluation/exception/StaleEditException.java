package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class StaleEditException extends ConflictException {
    public StaleEditException(Long gradeItemId) {
        super("La nota " + gradeItemId + " cambió desde que la viste");
    }
}
