package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class CreditNoteNotFoundException extends NotFoundException {
    public CreditNoteNotFoundException(Long id) {
        super("CreditNote " + id + " not found");
    }
}
