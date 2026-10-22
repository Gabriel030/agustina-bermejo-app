package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.collections.domain.ReminderSuspensionRule;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ReminderContentTermsCache {
    private final Map<LocalDate, Optional<LatePaymentTerms>> termsByDueDate = new HashMap<>();
    private Optional<ReminderSuspensionRule> suspensionRule;

    Optional<LatePaymentTerms> latePaymentTermsOn(LocalDate dueDate, Function<LocalDate, Optional<LatePaymentTerms>> loader) {
        return termsByDueDate.computeIfAbsent(dueDate, loader);
    }

    Optional<ReminderSuspensionRule> suspensionRuleFrom(Supplier<Optional<ReminderSuspensionRule>> loader) {
        if (suspensionRule == null) {
            suspensionRule = loader.get();
        }
        return suspensionRule;
    }
}
