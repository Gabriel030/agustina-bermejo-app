package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.operation.DirectorApprovable;
import com.red_escuela.backend.billing.operation.ResumedFromAuthorizationRequest;

import java.time.LocalDate;
import java.util.Objects;

public record CreateDiscountDefinitionCommand(DiscountDefinitionDraft draft, Long requestedByPersonId, boolean requestedByDirector, Long authorizationRequestId)
        implements DirectorApprovable, ResumedFromAuthorizationRequest<CreateDiscountDefinitionCommand> {

    public CreateDiscountDefinitionCommand {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(requestedByPersonId, "requestedByPersonId");
    }

    public static CreateDiscountDefinitionCommand byPerson(DiscountDefinitionDraft draft, Long requestedByPersonId, boolean requestedByDirector) {
        return new CreateDiscountDefinitionCommand(draft, requestedByPersonId, requestedByDirector, null);
    }

    @Override
    public String authorizationReason() {
        return "Alta de la promoción " + draft.name();
    }

    @Override
    public LocalDate authorizationExpiresOn() {
        return null;
    }

    @Override
    public CreateDiscountDefinitionCommand resumedFromAuthorizationRequest(Long authorizationRequestId) {
        return new CreateDiscountDefinitionCommand(draft, requestedByPersonId, requestedByDirector, authorizationRequestId);
    }
}
