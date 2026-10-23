package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.Money;

import java.util.Objects;

public record OverdueAccountBalance(Long studentId, int overdueInstallments, int oldestDaysOverdue, Money overdueAmount) {

    public OverdueAccountBalance {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(overdueAmount, "overdueAmount");
    }
}
