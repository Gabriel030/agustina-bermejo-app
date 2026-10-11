package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.CreditedInvoiceFacts;

public record InvoiceCancellationContext(CancelInvoiceCommand command, CreditedInvoiceFacts invoice) {
}
