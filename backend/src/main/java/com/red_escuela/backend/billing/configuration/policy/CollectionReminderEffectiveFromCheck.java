package com.red_escuela.backend.billing.configuration.policy;

import java.time.LocalDate;

public record CollectionReminderEffectiveFromCheck(LocalDate effectiveFrom, LocalDate today) implements EffectiveDated {
}
