package com.red_escuela.backend.billing.annualupfront.dto;

import com.red_escuela.backend.billing.annualupfront.service.DiscardedAnnualUpfrontRequest;

public record DiscardedAnnualUpfrontRequestResponse(Long id, String status, String discardReason, Long invoiceId) {

    public static DiscardedAnnualUpfrontRequestResponse from(DiscardedAnnualUpfrontRequest discarded) {
        return new DiscardedAnnualUpfrontRequestResponse(discarded.id(), discarded.status(), discarded.discardReason(), discarded.invoiceId());
    }
}
