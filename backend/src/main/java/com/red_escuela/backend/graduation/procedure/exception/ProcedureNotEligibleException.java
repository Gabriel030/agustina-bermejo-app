package com.red_escuela.backend.graduation.procedure.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ProcedureNotEligibleException extends UnprocessableEntityException implements ReportsErrorCode {

    public ProcedureNotEligibleException() {
        super("Por ahora no podés pedir este trámite");
    }
}
