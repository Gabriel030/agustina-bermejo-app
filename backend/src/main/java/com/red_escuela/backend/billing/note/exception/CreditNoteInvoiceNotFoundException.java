package com.red_escuela.backend.billing.note.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class CreditNoteInvoiceNotFoundException extends NotFoundException {

    public CreditNoteInvoiceNotFoundException(Long invoiceId) {
        super("Invoice not found: " + invoiceId);
    }
}
