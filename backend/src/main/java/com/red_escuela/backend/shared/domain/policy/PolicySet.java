package com.red_escuela.backend.shared.domain.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PolicySet<C> {
    private static final String UNNAMED_MOMENT = "UNNAMED";

    private final String moment;
    private final List<Policy<C>> ordered;

    private PolicySet(String moment, List<Policy<C>> ordered) {
        this.moment = moment;
        this.ordered = List.copyOf(ordered);
    }

    public static <C> PolicySet<C> of(List<Policy<C>> ordered) {
        return new PolicySet<>(UNNAMED_MOMENT, ordered);
    }

    public static <C> PolicySet<C> named(String moment, List<Policy<C>> ordered) {
        Objects.requireNonNull(moment, "moment");
        if (moment.isBlank()) {
            throw new IllegalArgumentException("A policy set needs a moment");
        }
        return new PolicySet<>(moment, ordered);
    }

    public String moment() {
        return moment;
    }

    public List<Policy<C>> policies() {
        return ordered;
    }

    public PolicyEvaluationResult evaluateRulesUntilFirstViolation(C context) {
        List<PolicyVerdict> verdicts = new ArrayList<>();
        for (Policy<C> policy : ordered) {
            PolicyVerdict verdict = policy.evaluateRule(context);
            verdicts.add(verdict);
            if (!verdict.allowed()) {
                break;
            }
        }
        return new PolicyEvaluationResult(verdicts);
    }

    public PolicyEvaluationResult evaluateAllRules(C context) {
        return new PolicyEvaluationResult(ordered.stream().map(policy -> policy.evaluateRule(context)).toList());
    }
}
