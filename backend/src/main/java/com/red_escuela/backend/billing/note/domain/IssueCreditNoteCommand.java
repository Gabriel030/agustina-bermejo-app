package com.red_escuela.backend.billing.note.domain;

import com.red_escuela.backend.billing.operation.DirectorApprovable;
import com.red_escuela.backend.billing.operation.InvoiceTargetedRequest;
import com.red_escuela.backend.billing.operation.ResumedFromAuthorizationRequest;
import com.red_escuela.backend.billing.payment.operation.IdempotencyKeyed;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record IssueCreditNoteCommand(Long invoiceId, CreditNoteReason reason, String justification, List<CreditNoteLineAmount> lines,
                                     CreditNoteDestination expectedDestination, Long requestedByPersonId, boolean requestedByDirector,
                                     Long authorizationRequestId, String idempotencyKey)
        implements DirectorApprovable, InvoiceTargetedRequest, ResumedFromAuthorizationRequest<IssueCreditNoteCommand>, IdempotencyKeyed {

    public IssueCreditNoteCommand {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(expectedDestination, "expectedDestination");
        Objects.requireNonNull(requestedByPersonId, "requestedByPersonId");
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public IssueCreditNoteCommand(Long invoiceId, CreditNoteReason reason, String justification, List<CreditNoteLineAmount> lines,
                                  CreditNoteDestination expectedDestination, Long requestedByPersonId, boolean requestedByDirector, Long authorizationRequestId) {
        this(invoiceId, reason, justification, lines, expectedDestination, requestedByPersonId, requestedByDirector, authorizationRequestId, null);
    }

    @Override
    public StatusChangeActor actor() {
        return new StatusChangeActor(requestedByPersonId, null);
    }

    @Override
    public Object requestFingerprint() {
        return new CreditNoteRequestFingerprint(invoiceId, reason, justification, lines, requestedByPersonId);
    }

    @Override
    public List<Long> targetedInvoiceIds() {
        return List.of(invoiceId);
    }

    @Override
    public String authorizationReason() {
        return "Nota de crédito (" + reason + ") sobre la factura " + invoiceId + ": " + justification;
    }

    @Override
    public LocalDate authorizationExpiresOn() {
        return null;
    }

    @Override
    public IssueCreditNoteCommand resumedFromAuthorizationRequest(Long authorizationRequestId) {
        return new IssueCreditNoteCommand(invoiceId, reason, justification, lines, expectedDestination, requestedByPersonId, requestedByDirector,
                authorizationRequestId, idempotencyKey);
    }
}
