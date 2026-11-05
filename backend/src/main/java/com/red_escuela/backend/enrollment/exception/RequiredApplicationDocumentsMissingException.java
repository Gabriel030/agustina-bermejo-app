package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.util.Set;

public class RequiredApplicationDocumentsMissingException extends BadRequestException {
    public RequiredApplicationDocumentsMissingException(Set<String> missing) {
        super("Faltan archivos de la solicitud: " + missing);
    }
}
