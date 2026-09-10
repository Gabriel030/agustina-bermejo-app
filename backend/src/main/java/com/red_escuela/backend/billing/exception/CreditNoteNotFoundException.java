package com.red_escuela.backend.billing.exception;

public class CreditNoteNotFoundException extends RuntimeException {
    public CreditNoteNotFoundException(Long id) {
        super("CreditNote " + id + " not found");
    }
}
