package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ReminderStudentFacts(Long studentId, List<ReminderInvoiceFact> invoices, Map<Currency, NonNegativeMoney> creditByCurrency) {

    public ReminderStudentFacts {
        Objects.requireNonNull(studentId, "studentId");
        invoices = List.copyOf(invoices);
        creditByCurrency = Map.copyOf(creditByCurrency);
    }
}
