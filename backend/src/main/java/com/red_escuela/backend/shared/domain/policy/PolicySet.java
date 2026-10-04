package com.red_escuela.backend.shared.domain.policy;

import java.util.ArrayList;
import java.util.List;

public final class PolicySet<C> {
    private final List<Policy<C>> ordered;

    private PolicySet(List<Policy<C>> ordered) {
        this.ordered = List.copyOf(ordered);
    }

    public static <C> PolicySet<C> of(List<Policy<C>> ordered) {
        return new PolicySet<>(ordered);
    }

    public PolicyEvaluation evaluateUntilFirstDenial(C context) {
        List<PolicyVerdict> verdicts = new ArrayList<>();
        for (Policy<C> policy : ordered) {
            PolicyVerdict verdict = policy.evaluate(context);
            verdicts.add(verdict);
            if (!verdict.allowed()) {
                break;
            }
        }
        return new PolicyEvaluation(verdicts);
    }

    public PolicyEvaluation evaluateAll(C context) {
        return new PolicyEvaluation(ordered.stream().map(policy -> policy.evaluate(context)).toList());
    }
}
