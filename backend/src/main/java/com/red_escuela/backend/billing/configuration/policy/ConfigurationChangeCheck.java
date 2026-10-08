package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;

import java.time.LocalDate;

public record ConfigurationChangeCheck(ConfigurationScope scope, String topicKey, LocalDate effectiveFrom, LocalDate today, String changeReason)
        implements EffectiveDated {
}
