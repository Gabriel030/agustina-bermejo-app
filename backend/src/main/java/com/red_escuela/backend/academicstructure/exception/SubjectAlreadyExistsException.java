package com.red_escuela.backend.academicstructure.exception;

public class SubjectAlreadyExistsException extends RuntimeException {
    public SubjectAlreadyExistsException(String name) {
        super("Ya existe un Subject con nombre: " + name);
    }
}
