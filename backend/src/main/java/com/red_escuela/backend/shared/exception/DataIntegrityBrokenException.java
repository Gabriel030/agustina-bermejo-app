package com.red_escuela.backend.shared.exception;

public abstract class DataIntegrityBrokenException extends RuntimeException {
    protected DataIntegrityBrokenException(String message) {
        super(message);
    }
}
