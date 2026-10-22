package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.ReminderLateFeeTerms;
import com.red_escuela.backend.billing.collections.domain.ReminderSuspensionRule;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ReminderContentTermsCache {
    private final Map<LocalDate, Optional<ReminderLateFeeTerms>> lateFeeByDueDate = new HashMap<>();
    private Optional<ReminderSuspensionRule> suspensionRule;

    Optional<ReminderLateFeeTerms> lateFeeTermsOn(LocalDate dueDate, Function<LocalDate, Optional<ReminderLateFeeTerms>> loader) {
        return lateFeeByDueDate.computeIfAbsent(dueDate, loader);
    }

    Optional<ReminderSuspensionRule> suspensionRuleFrom(Supplier<Optional<ReminderSuspensionRule>> loader) {
        if (suspensionRule == null) {
            suspensionRule = loader.get();
        }
        return suspensionRule;
    }
}
