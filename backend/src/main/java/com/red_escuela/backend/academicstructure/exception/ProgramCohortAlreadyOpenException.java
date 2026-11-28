package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ProgramCohortAlreadyOpenException extends ConflictException {
    public ProgramCohortAlreadyOpenException(Long cohortId) {
        super("La cohorte " + cohortId + " ya está abierta");
    }
}
