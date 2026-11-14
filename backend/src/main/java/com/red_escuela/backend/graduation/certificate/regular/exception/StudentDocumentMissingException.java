package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class StudentDocumentMissingException extends ConflictException {

    public StudentDocumentMissingException() {
        super("El alumno no tiene documento cargado: completá su DNI antes de emitir el certificado");
    }
}
