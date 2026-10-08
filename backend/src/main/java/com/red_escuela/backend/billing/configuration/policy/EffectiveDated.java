package com.red_escuela.backend.billing.configuration.policy;

import java.time.LocalDate;

public interface EffectiveDated {
    LocalDate effectiveFrom();

    LocalDate today();
}
