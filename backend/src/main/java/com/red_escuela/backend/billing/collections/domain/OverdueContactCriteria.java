package com.red_escuela.backend.billing.collections.domain;

public record OverdueContactCriteria(int daysOverdue, int overdueInstallments) {

    public OverdueContactCriteria {
        if (daysOverdue < 1) {
            throw new IllegalArgumentException("daysOverdue must be at least 1");
        }
        if (overdueInstallments < 1) {
            throw new IllegalArgumentException("overdueInstallments must be at least 1");
        }
    }

    public boolean isMetBy(int oldestDaysOverdue, int installmentsOverdue) {
        return oldestDaysOverdue >= daysOverdue || installmentsOverdue >= overdueInstallments;
    }
}
