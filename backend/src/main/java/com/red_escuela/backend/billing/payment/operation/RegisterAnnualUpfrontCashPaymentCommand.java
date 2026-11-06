package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.operation.IdempotencyKeyed;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.util.Objects;

public record RegisterAnnualUpfrontCashPaymentCommand(Long studentId, Long requestId, Money amount, String receiptNumber, String idempotencyKey, StatusChangeActor actor)
        implements IdempotencyKeyed {

    public RegisterAnnualUpfrontCashPaymentCommand {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(actor, "actor");
    }
}
