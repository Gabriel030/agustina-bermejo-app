package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class RejectionReasonInvalidException extends BadRequestException {

    public RejectionReasonInvalidException() {
        super("El motivo del rechazo debe tener entre 10 y 500 caracteres");
    }
}
