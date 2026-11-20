package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class RevocationReasonInvalidException extends BadRequestException {

    public RevocationReasonInvalidException() {
        super("El motivo de la revocación debe tener entre 10 y 500 caracteres");
    }
}
