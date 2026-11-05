package com.red_escuela.backend.billing.collections.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReminderImputationOrder {

    private ReminderImputationOrder() {
    }

    public static List<ReminderInvoiceFact> sortMostOverdueFirst(List<ReminderInvoiceFact> facts) {
        Map<Long, List<ReminderInvoiceFact>> byRoot = new LinkedHashMap<>();
        for (ReminderInvoiceFact fact : facts) {
            byRoot.computeIfAbsent(rootIdOf(fact), key -> new ArrayList<>()).add(fact);
        }
        return byRoot.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<Long, List<ReminderInvoiceFact>> entry) -> rootDueDateOf(entry.getValue())).thenComparing(Map.Entry::getKey))
                .flatMap(entry -> entry.getValue().stream().sorted(Comparator.comparing(ReminderInvoiceFact::isDebitNote)
                        .thenComparing(ReminderInvoiceFact::dueDate).thenComparing(ReminderInvoiceFact::invoiceId)))
                .toList();
    }

    private static Long rootIdOf(ReminderInvoiceFact fact) {
        return fact.isDebitNote() && fact.originInvoiceId() != null ? fact.originInvoiceId() : fact.invoiceId();
    }

    private static LocalDate rootDueDateOf(List<ReminderInvoiceFact> group) {
        return group.stream().filter(fact -> !fact.isDebitNote()).map(ReminderInvoiceFact::dueDate).min(Comparator.naturalOrder())
                .orElseGet(() -> group.stream().map(ReminderInvoiceFact::dueDate).min(Comparator.naturalOrder()).orElseThrow());
    }
}
