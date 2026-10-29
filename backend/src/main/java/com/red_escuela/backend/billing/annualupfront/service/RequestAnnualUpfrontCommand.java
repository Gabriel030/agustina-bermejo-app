package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.payment.operation.IdempotencyKeyed;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.util.Objects;

public record RequestAnnualUpfrontCommand(Long programEnrollmentId, boolean termsAccepted, String termsVersion, String idempotencyKey, StatusChangeActor actor)
        implements IdempotencyKeyed {

    public RequestAnnualUpfrontCommand {
        Objects.requireNonNull(programEnrollmentId, "programEnrollmentId");
        Objects.requireNonNull(actor, "actor");
    }
}
