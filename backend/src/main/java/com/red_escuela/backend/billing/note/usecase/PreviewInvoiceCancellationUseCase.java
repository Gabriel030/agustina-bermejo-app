package com.red_escuela.backend.billing.note.usecase;

import com.red_escuela.backend.billing.note.domain.InvoiceCancellationPreview;
import com.red_escuela.backend.billing.note.dto.CancelInvoiceRequest;

public interface PreviewInvoiceCancellationUseCase {
    InvoiceCancellationPreview previewInvoiceCancellation(Long invoiceId, CancelInvoiceRequest request);
}
