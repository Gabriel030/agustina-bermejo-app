package com.red_escuela.backend.evaluation.exception;

// Primera autorización a nivel de fila del sistema (no solo de rol): el Docente nunca puede
// operar sobre una comisión que no es suya, aunque tenga el rol DOCENTE.
public class NotOwnSectionException extends RuntimeException {
    public NotOwnSectionException(Long instructorId, Long sectionId) {
        super("El Docente " + instructorId + " no integra la comisión " + sectionId);
    }
}
