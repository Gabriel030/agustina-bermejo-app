package com.red_escuela.backend.exception;

// Spec Inscripciones académicas, servicio SubjectSectionEnrollmentService.cambiarComision:
// la comisión destino tiene que ser de la MISMA materia que la fila origen — si no, no es
// "cambiar de comisión", es inscribirse a otra materia (usa inscribir()).
public class InvalidSectionChangeException extends RuntimeException {
    public InvalidSectionChangeException(String message) {
        super(message);
    }
}
