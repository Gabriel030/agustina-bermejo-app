package com.red_escuela.backend.billing.collections.domain;

import java.time.LocalDate;
import java.util.Objects;

public record MonthCloseFacts(LocalDate cutoff, int overdueInstallmentsAtCutoff, LocalDate oldestOverdueInstallmentDueDate) {

    public MonthCloseFacts {
        Objects.requireNonNull(cutoff, "cutoff");
        if (overdueInstallmentsAtCutoff < 0) {
            throw new IllegalArgumentException("The overdue installment count must not be negative");
        }
        if ((overdueInstallmentsAtCutoff > 0) != (oldestOverdueInstallmentDueDate != null)) {
            throw new IllegalArgumentException("The oldest overdue installment date is present exactly when there are overdue installments");
        }
    }
}
