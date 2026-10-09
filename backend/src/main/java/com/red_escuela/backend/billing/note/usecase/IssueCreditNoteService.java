package com.red_escuela.backend.billing.note.usecase;

import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.dto.CreateCreditNoteRequest;
import com.red_escuela.backend.billing.note.service.CreditNoteCommandFactory;
import com.red_escuela.backend.billing.note.service.IssueCreditNoteOperation;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class IssueCreditNoteService implements IssueCreditNoteUseCase {
    private final BillingOperationPipeline pipeline;
    private final IssueCreditNoteOperation operation;
    private final CreditNoteCommandFactory commandFactory;

    public IssueCreditNoteService(BillingOperationPipeline pipeline, IssueCreditNoteOperation operation, CreditNoteCommandFactory commandFactory) {
        this.pipeline = pipeline;
        this.operation = operation;
        this.commandFactory = commandFactory;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public BillingOperationOutcome<CreditNote> issueOrRequestCreditNote(Long invoiceId, CreateCreditNoteRequest request) {
        return pipeline.runOperation(operation, commandFactory.buildCommandForCurrentUser(invoiceId, request.reason(), request.justification(), request.toLineAmounts()));
    }
}
