package com.red_escuela.backend.billing.note.usecase;

import com.red_escuela.backend.billing.note.domain.InvoiceCancellationPreview;
import com.red_escuela.backend.billing.note.dto.CancelInvoiceRequest;
import com.red_escuela.backend.billing.note.service.InvoiceCancellationPreviewService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class PreviewInvoiceCancellationService implements PreviewInvoiceCancellationUseCase {
    private final InvoiceCancellationPreviewService previewService;

    public PreviewInvoiceCancellationService(InvoiceCancellationPreviewService previewService) {
        this.previewService = previewService;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public InvoiceCancellationPreview previewInvoiceCancellation(Long invoiceId, CancelInvoiceRequest request) {
        return previewService.previewCancellationOf(invoiceId, request.reason(), request.justification());
    }
}
