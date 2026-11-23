package com.red_escuela.backend.graduation.debtexception.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class DocumentRequiresPaymentsUpToDateException extends ConflictException implements ReportsErrorCode {

    public DocumentRequiresPaymentsUpToDateException() {
        super("Para generar este documento tenés que estar al día con los pagos. Consultá en Administración.");
    }
}
