package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ForbiddenException;

public class NotOwnSectionException extends ForbiddenException {
    public NotOwnSectionException(Long instructorId, Long sectionId) {
        super("El Docente " + instructorId + " no integra la comisión " + sectionId);
    }
}
