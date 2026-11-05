package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.util.Objects;

public record DiscardAnnualUpfrontRequestCommand(Long requestId, Long programEnrollmentId, StatusChangeActor actor) {

    public DiscardAnnualUpfrontRequestCommand {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(programEnrollmentId, "programEnrollmentId");
        Objects.requireNonNull(actor, "actor");
    }
}
