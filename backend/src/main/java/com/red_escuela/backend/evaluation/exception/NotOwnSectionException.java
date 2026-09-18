package com.red_escuela.backend.evaluation.exception;

public class NotOwnSectionException extends RuntimeException {
    public NotOwnSectionException(Long instructorId, Long sectionId) {
        super("El Docente " + instructorId + " no integra la comisión " + sectionId);
    }
}
