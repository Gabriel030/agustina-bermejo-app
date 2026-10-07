package com.red_escuela.backend.billing.invoice.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CreditNoteExceedsCreditableAmountException extends ConflictException {

    public CreditNoteExceedsCreditableAmountException() {
        super("The credit exceeds the creditable amount");
    }
}
