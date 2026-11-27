package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.academicstructure.domain.CohortIntake;
import com.red_escuela.backend.shared.exception.ConflictException;

public class ProgramCohortAlreadyExistsException extends ConflictException {
    public ProgramCohortAlreadyExistsException(Long programId, Long termId, CohortIntake intake) {
        super("Ya existe la cohorte " + intake + " de la carrera " + programId + " en el período " + termId);
    }
}
