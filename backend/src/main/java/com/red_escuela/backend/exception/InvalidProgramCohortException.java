package com.red_escuela.backend.exception;

// Validación de forma de ProgramCohort: ordenEnAnio fuera de {1, 2} (política
// institucional, exactamente 2 cohortes por año) o ya existe una cohorte con
// ese mismo orden para la carrera.
public class InvalidProgramCohortException extends RuntimeException {
    public InvalidProgramCohortException(String message) {
        super(message);
    }
}
