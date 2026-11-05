package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;
import java.util.function.Function;

public record RefundPlanOutcome(List<RefundPart> parts, List<PolicyViolation> violations) {

    public RefundPlanOutcome {
        parts = List.copyOf(parts);
        violations = List.copyOf(violations);
    }

    public static RefundPlanOutcome planned(List<RefundPart> parts) {
        return new RefundPlanOutcome(parts, List.of());
    }

    public static RefundPlanOutcome blocked(List<PolicyViolation> violations) {
        return new RefundPlanOutcome(List.of(), violations);
    }

    public static RefundPlanOutcome plannedButBlocked(List<RefundPart> parts, List<PolicyViolation> violations) {
        return new RefundPlanOutcome(parts, violations);
    }

    public boolean isAllowed() {
        return violations.isEmpty();
    }

    public List<RefundPart> partsOrThrow(Function<List<PolicyViolation>, ? extends RuntimeException> exceptionFactory) {
        if (!violations.isEmpty()) {
            throw exceptionFactory.apply(violations);
        }
        return parts;
    }
}
