package com.red_escuela.backend.billing.payment.domain;

import java.util.List;

public record RefundRequestResult(Long authorizationRequestId, List<Refund> parts) {

    public RefundRequestResult {
        parts = List.copyOf(parts);
    }

    public boolean isAwaitingAuthorization() {
        return authorizationRequestId != null;
    }
}
