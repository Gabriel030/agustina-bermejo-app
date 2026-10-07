package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.shared.exception.ConflictException;

public class SubjectNotOpenableForSectionsException extends ConflictException {
    public SubjectNotOpenableForSectionsException(Long id, SubjectStatus status) {
        super("La materia " + id + " en estado " + status + " no admite comisiones hasta que esté aprobada");
    }
}
