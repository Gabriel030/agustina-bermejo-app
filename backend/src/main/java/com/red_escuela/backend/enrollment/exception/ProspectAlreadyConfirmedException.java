package com.red_escuela.backend.enrollment.exception;

/**
 * Solo se usa en confirmarAlta (transición de estado real). Subir un documento
 * sobre un Student ya CONFIRMADO está permitido, no dispara esta excepción.
 */
public class ProspectAlreadyConfirmedException extends RuntimeException {
    public ProspectAlreadyConfirmedException(Long studentId) {
        super("El prospecto ya fue confirmado: " + studentId);
    }
}
