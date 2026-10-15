package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.operation.DirectorApprovable;

import java.time.LocalDate;
import java.util.Objects;

public record CloseDiscountDefinitionCommand(Long discountDefinitionId, String reason, Long requestedByPersonId, boolean requestedByDirector)
        implements DirectorApprovable {

    public CloseDiscountDefinitionCommand {
        Objects.requireNonNull(discountDefinitionId, "discountDefinitionId");
        Objects.requireNonNull(requestedByPersonId, "requestedByPersonId");
    }

    @Override
    public String authorizationReason() {
        return "Cierre de la promoción " + discountDefinitionId + ": " + reason.strip();
    }

    @Override
    public LocalDate authorizationExpiresOn() {
        return null;
    }
}
