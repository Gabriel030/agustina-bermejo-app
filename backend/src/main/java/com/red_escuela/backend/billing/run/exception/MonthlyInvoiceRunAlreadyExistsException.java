package com.red_escuela.backend.billing.run.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

import java.time.YearMonth;

public class MonthlyInvoiceRunAlreadyExistsException extends ConflictException {

    public MonthlyInvoiceRunAlreadyExistsException(YearMonth period) {
        super("The period " + period + " already has a real monthly invoice run");
    }
}
