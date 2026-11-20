package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SectionNotOpenForEnrollmentException extends ConflictException {
    public SectionNotOpenForEnrollmentException(Long sectionId) {
        super("La comisión " + sectionId + " no está abierta a inscripción: está inactiva o su oferta aún no fue aprobada");
    }
}
