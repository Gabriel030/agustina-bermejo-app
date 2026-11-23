package com.red_escuela.backend.graduation.debtexception.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class DebtExceptionAlreadyUsedException extends ConflictException implements ReportsErrorCode {

    public DebtExceptionAlreadyUsedException() {
        super("Esta constancia ya se descargó y no se puede descargar de nuevo");
    }
}
