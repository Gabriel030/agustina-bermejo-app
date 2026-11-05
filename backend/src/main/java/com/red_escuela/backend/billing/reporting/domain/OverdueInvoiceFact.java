package com.red_escuela.backend.billing.reporting.domain;

import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;

public record OverdueInvoiceFact(Long invoiceId, Long studentId, LocalDate dueDate, Money outstanding, Set<Long> programIds, Set<Long> courseIds) {

    public OverdueInvoiceFact {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(dueDate, "dueDate");
        Objects.requireNonNull(outstanding, "outstanding");
        programIds = Set.copyOf(programIds);
        courseIds = Set.copyOf(courseIds);
        if (outstanding.isNegative()) {
            throw new IllegalArgumentException("The outstanding amount of an overdue invoice cannot be negative");
        }
    }

    public OverdueAgeBucket bucketOn(LocalDate businessDate) {
        return OverdueAgeBucket.forDaysOverdue(ChronoUnit.DAYS.between(dueDate, businessDate));
    }
}
