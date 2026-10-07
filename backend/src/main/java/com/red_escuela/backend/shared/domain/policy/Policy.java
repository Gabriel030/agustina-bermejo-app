package com.red_escuela.backend.shared.domain.policy;

public interface Policy<C> {
    String ruleName();

    PolicyVerdict evaluateRule(C context);
}
