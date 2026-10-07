package com.red_escuela.backend.billing.invoice.exception;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentEvent;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.shared.exception.ConflictException;

public class IllegalInvoicePaymentStatusTransitionException extends ConflictException {

    public IllegalInvoicePaymentStatusTransitionException(InvoicePaymentStatus fromStatus, InvoicePaymentEvent event) {
        super("Event " + event + " is not allowed from payment status " + fromStatus);
    }
}
