package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SectionEvaluationSchemeLockedException extends ConflictException {
    public SectionEvaluationSchemeLockedException(Long sectionId) {
        super("No se puede cambiar el esquema de evaluación de la comisión " + sectionId + " porque ya tiene notas cargadas");
    }
}
