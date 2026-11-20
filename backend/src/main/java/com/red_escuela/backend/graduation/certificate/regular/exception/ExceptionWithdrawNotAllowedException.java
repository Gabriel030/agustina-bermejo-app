package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.ForbiddenException;

public class ExceptionWithdrawNotAllowedException extends ForbiddenException {

    public ExceptionWithdrawNotAllowedException() {
        super("Solo quien pidió la excepción puede retirarla");
    }
}
