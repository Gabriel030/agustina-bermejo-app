package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.util.Objects;

public record FailRefundCommand(Long refundId, String reason, StatusChangeActor actor) {

    public FailRefundCommand {
        Objects.requireNonNull(refundId, "refundId");
        Objects.requireNonNull(actor, "actor");
    }
}
