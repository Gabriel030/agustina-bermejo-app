package com.red_escuela.backend.billing.reporting.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;

import java.util.Objects;
import java.util.Optional;

public record MetricFilter(MonthRange range, Currency currency, Long programId, Long courseId, InvoiceType invoiceType) {

    public MetricFilter {
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(currency, "currency");
    }

    public Optional<Long> program() {
        return Optional.ofNullable(programId);
    }

    public Optional<Long> course() {
        return Optional.ofNullable(courseId);
    }

    public Optional<InvoiceType> type() {
        return Optional.ofNullable(invoiceType);
    }
}
