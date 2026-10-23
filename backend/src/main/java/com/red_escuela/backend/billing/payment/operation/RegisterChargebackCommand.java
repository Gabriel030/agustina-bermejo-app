package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.time.LocalDate;
import java.util.Objects;

public record RegisterChargebackCommand(Long paymentId, Money amount, LocalDate chargedBackOn, String reference, StatusChangeActor actor) {

    public RegisterChargebackCommand {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(actor, "actor");
    }
}
