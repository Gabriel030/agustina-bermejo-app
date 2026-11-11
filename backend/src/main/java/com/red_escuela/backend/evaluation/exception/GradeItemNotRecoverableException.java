package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class GradeItemNotRecoverableException extends ConflictException {
    public GradeItemNotRecoverableException(Long gradeItemId) {
        super("La nota " + gradeItemId + " no es un parcial: solo los parciales tienen recuperatorio");
    }
}
