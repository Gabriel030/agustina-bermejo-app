package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class GradeItemNotPublishedException extends ConflictException {
    public GradeItemNotPublishedException(Long gradeItemId) {
        super("La nota " + gradeItemId + " no está publicada: solo se corrigen notas ya publicadas");
    }
}
