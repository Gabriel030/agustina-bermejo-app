package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

public record RefundBlockResponse(String code, String message) {

    public static RefundBlockResponse from(PolicyViolation violation) {
        return violation == null ? null : new RefundBlockResponse(violation.code(), violation.message());
    }
}
