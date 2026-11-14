package com.red_escuela.backend.dataimport.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class BillingModeNotOffException extends ConflictException implements ReportsErrorCode {
    public BillingModeNotOffException() {
        super("La importación exige la facturación apagada");
    }
}
