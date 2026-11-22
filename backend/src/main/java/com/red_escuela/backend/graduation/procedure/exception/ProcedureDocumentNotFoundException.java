package com.red_escuela.backend.graduation.procedure.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ProcedureDocumentNotFoundException extends NotFoundException implements ReportsErrorCode {

    public ProcedureDocumentNotFoundException() {
        super("No encontramos el documento");
    }
}
