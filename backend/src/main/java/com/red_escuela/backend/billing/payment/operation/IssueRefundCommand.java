package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.operation.IdempotencyKeyed;
import com.red_escuela.backend.billing.operation.DirectorApprovable;
import com.red_escuela.backend.billing.operation.ResumedFromAuthorizationRequest;
import com.red_escuela.backend.billing.payment.domain.RefundDestination;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.time.LocalDate;
import java.util.Objects;

public record IssueRefundCommand(Long studentId, Money amount, String destinationAccount, String destinationHolder, Long requestedByPersonId,
                                 boolean requestedByDirector, Long authorizationRequestId, String idempotencyKey)
        implements DirectorApprovable, ResumedFromAuthorizationRequest<IssueRefundCommand>, IdempotencyKeyed {

    public IssueRefundCommand {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(requestedByPersonId, "requestedByPersonId");
    }

    public IssueRefundCommand(Long studentId, Money amount, String destinationAccount, String destinationHolder, Long requestedByPersonId,
                              boolean requestedByDirector, Long authorizationRequestId) {
        this(studentId, amount, destinationAccount, destinationHolder, requestedByPersonId, requestedByDirector, authorizationRequestId, null);
    }

    @Override
    public StatusChangeActor actor() {
        return new StatusChangeActor(requestedByPersonId, null);
    }

    @Override
    public Object requestFingerprint() {
        return new RefundRequestFingerprint(studentId, amount, destinationAccount, destinationHolder, requestedByPersonId);
    }

    public RefundDestination requestedDestination() {
        return new RefundDestination(destinationAccount, destinationHolder);
    }

    @Override
    public String authorizationReason() {
        String requested = amount == null ? "" : amount.amount().toPlainString() + " " + amount.currency();
        return "Devolución de saldo a favor de " + requested + " del alumno " + studentId;
    }

    @Override
    public LocalDate authorizationExpiresOn() {
        return null;
    }

    @Override
    public IssueRefundCommand resumedFromAuthorizationRequest(Long authorizationRequestId) {
        return new IssueRefundCommand(studentId, amount, destinationAccount, destinationHolder, requestedByPersonId, requestedByDirector, authorizationRequestId, idempotencyKey);
    }
}
