package com.red_escuela.backend.billing.invoice.exception;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentEvent;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.shared.exception.ConflictException;

public class IllegalInvoiceDocumentStatusTransitionException extends ConflictException {

    public IllegalInvoiceDocumentStatusTransitionException(InvoiceDocumentStatus fromStatus, InvoiceDocumentEvent event) {
        super("Event " + event + " is not allowed from document status " + fromStatus);
    }
}
