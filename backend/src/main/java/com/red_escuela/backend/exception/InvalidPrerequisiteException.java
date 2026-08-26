package com.red_escuela.backend.exception;

/**
 * Una correlatividad entre entradas ProgramSubject de carreras distintas no tiene
 * sentido de negocio (Spec Estructura académica, decisión 16).
 */
public class InvalidPrerequisiteException extends RuntimeException {
    public InvalidPrerequisiteException(String message) {
        super(message);
    }
}
