package com.red_escuela.backend.billing.note.usecase;

import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.domain.SystemCancellationOrigin;
import com.red_escuela.backend.billing.note.service.CancelInvoiceOperation;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CancelInvoiceBySystemService implements CancelInvoiceBySystemUseCase {
    private final BillingOperationPipeline pipeline;
    private final CancelInvoiceOperation operation;

    public CancelInvoiceBySystemService(BillingOperationPipeline pipeline, CancelInvoiceOperation operation) {
        this.pipeline = pipeline;
        this.operation = operation;
    }

    @Override
    public CreditNote cancelInvoiceWithoutDirector(Long invoiceId, SystemCancellationOrigin origin, String justification) {
        return pipeline.runOperation(operation, CancelInvoiceCommand.bySystem(invoiceId, origin, justification)).resultOrThrowIfAwaitingAuthorization(
                requestId -> new IllegalStateException("A system cancellation never waits for authorization: " + requestId));
    }
}
