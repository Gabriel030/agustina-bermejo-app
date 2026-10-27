package com.red_escuela.backend.billing.authorization.dto;

import com.red_escuela.backend.billing.authorization.domain.PendingInvoiceAuthorization;

import java.time.Instant;

public record PendingAuthorizationResponse(Long id, String type, Long requestedByPersonId, String requestedByName, Instant requestedAt) {

    public static PendingAuthorizationResponse from(PendingInvoiceAuthorization pending) {
        return pending == null ? null
                : new PendingAuthorizationResponse(pending.id(), pending.type().name(), pending.requestedByPersonId(), pending.requestedByName(), pending.requestedAt());
    }
}
