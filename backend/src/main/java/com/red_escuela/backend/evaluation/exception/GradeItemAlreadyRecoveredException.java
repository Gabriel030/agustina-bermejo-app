package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class GradeItemAlreadyRecoveredException extends ConflictException {
    public GradeItemAlreadyRecoveredException(Long gradeItemId) {
        super("El parcial " + gradeItemId + " ya tiene su recuperatorio cargado");
    }
}
