package com.red_escuela.backend.shared.domain.policy;

public interface Policy<C> {
    String name();

    PolicyVerdict evaluate(C context);
}
