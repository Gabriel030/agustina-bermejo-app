package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.operation.DirectorApprovable;
import com.red_escuela.backend.billing.operation.ResumedFromAuthorizationRequest;
import com.red_escuela.backend.billing.payment.domain.RefundDestination;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.util.Objects;

public record IssueRefundCommand(Long studentId, Money amount, String destinationAccount, String destinationHolder, Long requestedByPersonId,
                                 boolean requestedByDirector, Long authorizationRequestId) implements DirectorApprovable, ResumedFromAuthorizationRequest<IssueRefundCommand> {

    public IssueRefundCommand {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(requestedByPersonId, "requestedByPersonId");
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
        return new IssueRefundCommand(studentId, amount, destinationAccount, destinationHolder, requestedByPersonId, requestedByDirector, authorizationRequestId);
    }
}
