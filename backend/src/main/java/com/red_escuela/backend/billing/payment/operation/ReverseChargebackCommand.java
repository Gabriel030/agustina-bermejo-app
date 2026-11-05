package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.util.Objects;

public record ReverseChargebackCommand(Long chargebackId, StatusChangeActor actor) {

    public ReverseChargebackCommand {
        Objects.requireNonNull(chargebackId, "chargebackId");
        Objects.requireNonNull(actor, "actor");
    }
}
