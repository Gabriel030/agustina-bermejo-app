package com.red_escuela.backend.shared.storage;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class UnsupportedFileTypeException extends BadRequestException {
    public UnsupportedFileTypeException(String contentType) {
        super("Tipo de archivo no soportado: " + contentType);
    }
}
