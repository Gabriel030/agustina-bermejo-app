package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.payment.domain.PaymentMethodType;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.time.LocalDate;
import java.util.Objects;

public record CompleteRefundCommand(Long refundId, LocalDate completedOn, String reference, PaymentMethodType methodType, StatusChangeActor actor) {

    public CompleteRefundCommand {
        Objects.requireNonNull(refundId, "refundId");
        Objects.requireNonNull(actor, "actor");
    }
}
