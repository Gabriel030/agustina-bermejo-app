package com.red_escuela.backend.graduation.certificate.signatory.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class SignatoryRoleInvalidException extends UnprocessableEntityException {

    public SignatoryRoleInvalidException() {
        super("El rol del firmante es obligatorio y admite hasta 80 caracteres");
    }
}
