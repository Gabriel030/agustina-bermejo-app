package com.red_escuela.backend.billing.run.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class MonthlyInvoiceRunNotFoundException extends NotFoundException {

    public MonthlyInvoiceRunNotFoundException(Long id) {
        super("Monthly invoice run not found: " + id);
    }
}
