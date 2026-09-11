package com.red_escuela.backend.graduation.exception;

public class GraduationRequestNotFoundException extends RuntimeException {
    public GraduationRequestNotFoundException(Long id) {
        super("No existe el trámite de egreso " + id);
    }
}
