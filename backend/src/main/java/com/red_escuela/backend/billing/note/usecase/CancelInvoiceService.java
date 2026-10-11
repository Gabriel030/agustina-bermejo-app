package com.red_escuela.backend.billing.note.usecase;

import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.dto.CancelInvoiceRequest;
import com.red_escuela.backend.billing.note.service.CancelInvoiceOperation;
import com.red_escuela.backend.billing.note.service.CreditNoteCommandFactory;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class CancelInvoiceService implements CancelInvoiceUseCase {
    private final BillingOperationPipeline pipeline;
    private final CancelInvoiceOperation operation;
    private final CreditNoteCommandFactory commandFactory;

    public CancelInvoiceService(BillingOperationPipeline pipeline, CancelInvoiceOperation operation, CreditNoteCommandFactory commandFactory) {
        this.pipeline = pipeline;
        this.operation = operation;
        this.commandFactory = commandFactory;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public BillingOperationOutcome<CreditNote> cancelOrRequestInvoiceCancellation(Long invoiceId, CancelInvoiceRequest request) {
        return pipeline.runOperation(operation, commandFactory.buildCancellationCommandForCurrentUser(invoiceId, request.reason(), request.justification()));
    }
}
