package com.red_escuela.backend.billing.note.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.red_escuela.backend.billing.operation.DirectorApprovable;
import com.red_escuela.backend.billing.operation.ResumedFromAuthorizationRequest;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.time.LocalDate;
import java.util.Objects;

public record CancelInvoiceCommand(Long invoiceId, CreditNoteReason reason, String justification, Long requestedByPersonId, boolean requestedByDirector,
                                   SystemCancellationOrigin systemOrigin, Long authorizationRequestId)
        implements DirectorApprovable, ResumedFromAuthorizationRequest<CancelInvoiceCommand> {

    public CancelInvoiceCommand {
        Objects.requireNonNull(invoiceId, "invoiceId");
    }

    public static CancelInvoiceCommand byPerson(Long invoiceId, CreditNoteReason reason, String justification, Long requestedByPersonId, boolean requestedByDirector) {
        Objects.requireNonNull(requestedByPersonId, "requestedByPersonId");
        return new CancelInvoiceCommand(invoiceId, reason, justification, requestedByPersonId, requestedByDirector, null, null);
    }

    public static CancelInvoiceCommand bySystem(Long invoiceId, SystemCancellationOrigin origin, String justification) {
        Objects.requireNonNull(origin, "origin");
        return new CancelInvoiceCommand(invoiceId, CreditNoteReason.CANCELLATION, justification, null, false, origin, null);
    }

    @Override
    public boolean requestedByDirector() {
        return requestedByDirector || initiatedBySystem();
    }

    @JsonIgnore
    public boolean initiatedBySystem() {
        return systemOrigin != null;
    }

    @JsonIgnore
    public boolean releasesChargeKeys() {
        return initiatedBySystem() || reason == CreditNoteReason.BILLING_ERROR;
    }

    @JsonIgnore
    public StatusChangeActor actor() {
        return initiatedBySystem() ? StatusChangeActor.ofProcess(systemOrigin.processName()) : StatusChangeActor.ofPerson(requestedByPersonId);
    }

    @Override
    public String authorizationReason() {
        return "Cancelación (" + reason + ") de la factura " + invoiceId + ": " + justification;
    }

    @Override
    public LocalDate authorizationExpiresOn() {
        return null;
    }

    @Override
    public CancelInvoiceCommand resumedFromAuthorizationRequest(Long authorizationRequestId) {
        return new CancelInvoiceCommand(invoiceId, reason, justification, requestedByPersonId, requestedByDirector, systemOrigin, authorizationRequestId);
    }
}
