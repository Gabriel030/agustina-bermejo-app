package com.red_escuela.backend.exception;

public class ElectiveGroupNotFoundException extends RuntimeException {
    public ElectiveGroupNotFoundException(Long id) {
        super("ElectiveGroup no encontrado: " + id);
    }
}
