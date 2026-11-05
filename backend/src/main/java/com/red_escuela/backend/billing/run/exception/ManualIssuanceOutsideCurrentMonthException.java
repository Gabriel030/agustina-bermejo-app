package com.red_escuela.backend.billing.run.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ManualIssuanceOutsideCurrentMonthException extends ConflictException {

    public ManualIssuanceOutsideCurrentMonthException() {
        super("Manual issuance is only allowed for the current month");
    }
}
