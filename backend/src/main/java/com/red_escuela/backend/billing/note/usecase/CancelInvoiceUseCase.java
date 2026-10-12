package com.red_escuela.backend.billing.note.usecase;

import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.dto.CancelInvoiceRequest;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;

public interface CancelInvoiceUseCase {
    BillingOperationOutcome<CreditNote> cancelOrRequestInvoiceCancellation(Long invoiceId, CancelInvoiceRequest request);
}
