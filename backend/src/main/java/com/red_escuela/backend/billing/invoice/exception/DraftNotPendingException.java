package com.red_escuela.backend.billing.invoice.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class DraftNotPendingException extends ConflictException {

    public DraftNotPendingException(Long invoiceId) {
        super("Draft invoice " + invoiceId + " is not pending review in this run");
    }
}
