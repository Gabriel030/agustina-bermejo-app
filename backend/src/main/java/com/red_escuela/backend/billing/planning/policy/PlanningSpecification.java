package com.red_escuela.backend.billing.planning.policy;

public interface PlanningSpecification<C> {
    boolean isSatisfiedBy(C candidate);

    default PlanningSpecification<C> and(PlanningSpecification<C> other) {
        return candidate -> isSatisfiedBy(candidate) && other.isSatisfiedBy(candidate);
    }
}
