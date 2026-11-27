package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SubjectCriticalFieldsLockedException extends ConflictException {
    public SubjectCriticalFieldsLockedException(Long subjectId) {
        super("No se puede cambiar la modalidad de calificación, los intentos ni las horas de la materia " + subjectId
                + " porque está aprobada y tiene comisiones previas");
    }
}
