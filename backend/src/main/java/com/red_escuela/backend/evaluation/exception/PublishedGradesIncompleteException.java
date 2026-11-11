package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class PublishedGradesIncompleteException extends ConflictException {
    public PublishedGradesIncompleteException(Long sectionId) {
        super("La comisión " + sectionId + " tiene evaluaciones sin publicar: no se puede confirmar el cierre");
    }
}
