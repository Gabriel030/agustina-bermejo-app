package com.red_escuela.backend.graduation.procedure.exception;

import com.red_escuela.backend.shared.exception.ReportsErrorCode;
import com.red_escuela.backend.shared.exception.TooManyRequestsException;

public class IssuanceLimitReachedException extends TooManyRequestsException implements ReportsErrorCode {

    public IssuanceLimitReachedException() {
        super("Ya generaste hoy esta constancia. Si sigue vigente, podés volver a descargarla desde Trámites");
    }
}
