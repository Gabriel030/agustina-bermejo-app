package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;

public interface InvoiceIssuingContextLoader {

    InvoiceIssuingContext loadContextLockingRecipient(InvoiceIssuingRequest request);
}
