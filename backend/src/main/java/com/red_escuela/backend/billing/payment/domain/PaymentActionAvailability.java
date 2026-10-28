package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

public record PaymentActionAvailability(PolicyViolation blockedBy) {

    public static PaymentActionAvailability available() {
        return new PaymentActionAvailability(null);
    }

    public static PaymentActionAvailability blockedBy(PolicyViolation violation) {
        return new PaymentActionAvailability(violation);
    }

    public boolean allowed() {
        return blockedBy == null;
    }
}
