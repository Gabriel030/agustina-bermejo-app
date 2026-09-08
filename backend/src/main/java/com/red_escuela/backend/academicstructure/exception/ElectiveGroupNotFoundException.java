package com.red_escuela.backend.academicstructure.exception;

public class ElectiveGroupNotFoundException extends RuntimeException {
    public ElectiveGroupNotFoundException(Long id) {
        super("ElectiveGroup no encontrado: " + id);
    }
}
