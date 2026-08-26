package com.red_escuela.backend.exception;

public class UnsupportedFileTypeException extends RuntimeException {
    public UnsupportedFileTypeException(String contentType) {
        super("Tipo de archivo no soportado: " + contentType);
    }
}
