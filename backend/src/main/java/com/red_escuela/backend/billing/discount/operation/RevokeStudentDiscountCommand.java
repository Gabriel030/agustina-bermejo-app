package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.operation.DirectorApprovable;

import java.time.LocalDate;
import java.util.Objects;

public record RevokeStudentDiscountCommand(Long studentDiscountId, String reason, Long requestedByPersonId, boolean requestedByDirector, LocalDate requestedOn)
        implements DirectorApprovable {

    public RevokeStudentDiscountCommand {
        Objects.requireNonNull(studentDiscountId, "studentDiscountId");
        Objects.requireNonNull(requestedByPersonId, "requestedByPersonId");
        Objects.requireNonNull(requestedOn, "requestedOn");
    }

    @Override
    public String authorizationReason() {
        return "Revocación del descuento " + studentDiscountId + ": " + (reason == null ? "" : reason.strip());
    }

    @Override
    public LocalDate authorizationExpiresOn() {
        return requestedOn.withDayOfMonth(1).plusMonths(1);
    }
}
