package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.academicstructure.entity.SubjectStatus;

public class SubjectNotAssignableToPlanException extends RuntimeException {
    public SubjectNotAssignableToPlanException(Long id, SubjectStatus status) {
        super("La materia " + id + " en estado " + status + " no se puede agregar a un plan de estudios");
    }
}
