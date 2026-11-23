package com.red_escuela.backend.graduation.debtexception.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class DebtExceptionNotFoundException extends NotFoundException implements ReportsErrorCode {

    public DebtExceptionNotFoundException() {
        super("No existe la excepción");
    }
}
