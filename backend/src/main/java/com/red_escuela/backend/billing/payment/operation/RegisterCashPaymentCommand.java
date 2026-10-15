package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.time.LocalDate;
import java.util.Objects;

public record RegisterCashPaymentCommand(Long studentId, Money amount, LocalDate paidOn, String receiptNumber, String backdateReason,
                                         String previewFingerprint, boolean requestedByDirector, String idempotencyKey, StatusChangeActor actor)
        implements IdempotencyKeyed {

    public RegisterCashPaymentCommand {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(actor, "actor");
    }
}
