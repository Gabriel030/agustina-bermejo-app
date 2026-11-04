package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

import java.util.Set;

public class ApplicationDocumentsIncompleteException extends ConflictException {
    public ApplicationDocumentsIncompleteException(Long studentId, Set<String> missing) {
        super("El student " + studentId + " no tiene los documentos requeridos: " + missing);
    }
}
