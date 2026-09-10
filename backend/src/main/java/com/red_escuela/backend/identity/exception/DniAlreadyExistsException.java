package com.red_escuela.backend.identity.exception;

/**
 * Envuelve la violación de la constraint uk_person_dni (V2__critical_integrity.sql)
 * con un mensaje claro en vez de dejar propagar la DataIntegrityViolationException cruda.
 */
public class DniAlreadyExistsException extends RuntimeException {
    public DniAlreadyExistsException(String dni) {
        super("Ya existe una persona con DNI: " + dni);
    }
}
