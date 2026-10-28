package com.red_escuela.backend.billing.note.controller;

import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.dto.CancelInvoiceRequest;
import com.red_escuela.backend.billing.note.dto.CreditNotePendingAuthorizationResponse;
import com.red_escuela.backend.billing.note.dto.CreditNoteResponse;
import com.red_escuela.backend.billing.note.dto.InvoiceCancellationPreviewResponse;
import com.red_escuela.backend.billing.note.usecase.CancelInvoiceUseCase;
import com.red_escuela.backend.billing.note.usecase.PreviewInvoiceCancellationUseCase;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvoiceCancellationController {
    private final CancelInvoiceUseCase cancelInvoiceUseCase;

    private final PreviewInvoiceCancellationUseCase previewInvoiceCancellationUseCase;

    public InvoiceCancellationController(CancelInvoiceUseCase cancelInvoiceUseCase, PreviewInvoiceCancellationUseCase previewInvoiceCancellationUseCase) {
        this.cancelInvoiceUseCase = cancelInvoiceUseCase;
        this.previewInvoiceCancellationUseCase = previewInvoiceCancellationUseCase;
    }

    @PostMapping("/invoices/{invoiceId}/cancellations/preview")
    public InvoiceCancellationPreviewResponse previewInvoiceCancellation(@PathVariable Long invoiceId, @Valid @RequestBody CancelInvoiceRequest request) {
        return InvoiceCancellationPreviewResponse.from(previewInvoiceCancellationUseCase.previewInvoiceCancellation(invoiceId, request));
    }

    @PostMapping("/invoices/{invoiceId}/cancellations")
    public ResponseEntity<?> cancelOrRequestInvoiceCancellation(@PathVariable Long invoiceId, @Valid @RequestBody CancelInvoiceRequest request) {
        BillingOperationOutcome<CreditNote> outcome = cancelInvoiceUseCase.cancelOrRequestInvoiceCancellation(invoiceId, request);
        return switch (outcome) {
            case BillingOperationOutcome.Processed<CreditNote> processed -> ResponseEntity.status(HttpStatus.CREATED).body(CreditNoteResponse.from(processed.result()));
            case BillingOperationOutcome.AwaitingAuthorization<CreditNote> awaiting ->
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(new CreditNotePendingAuthorizationResponse(awaiting.authorizationRequestId()));
        };
    }
}
