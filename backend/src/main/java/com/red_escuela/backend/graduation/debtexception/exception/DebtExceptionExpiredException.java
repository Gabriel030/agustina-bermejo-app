package com.red_escuela.backend.graduation.debtexception.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class DebtExceptionExpiredException extends ConflictException implements ReportsErrorCode {

    public DebtExceptionExpiredException() {
        super("La excepción venció");
    }
}
