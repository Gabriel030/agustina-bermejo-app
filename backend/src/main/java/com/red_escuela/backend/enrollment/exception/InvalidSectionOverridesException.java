package com.red_escuela.backend.enrollment.exception;

// sectionOverrides viaja como JSON string en multipart (no hay forma razonable de
// mandar un Map en multipart sin infra nueva) — esta excepción cubre el caso de un
// JSON malformado que el cliente mandó a mano o con un bug.
public class InvalidSectionOverridesException extends RuntimeException {
    public InvalidSectionOverridesException(String sectionOverrides, Throwable cause) {
        super("sectionOverrides inválido: " + sectionOverrides, cause);
    }
}
