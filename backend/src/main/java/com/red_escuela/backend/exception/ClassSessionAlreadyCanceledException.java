package com.red_escuela.backend.exception;

public class ClassSessionAlreadyCanceledException extends RuntimeException {
    public ClassSessionAlreadyCanceledException(Long classSessionId) {
        super("La clase " + classSessionId + " ya está cancelada");
    }
}
