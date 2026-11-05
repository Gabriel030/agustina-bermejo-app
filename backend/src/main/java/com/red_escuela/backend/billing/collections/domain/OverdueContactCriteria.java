package com.red_escuela.backend.billing.collections.domain;

import java.util.Optional;

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
        return reasonFor(oldestDaysOverdue, installmentsOverdue).isPresent();
    }

    public Optional<OverdueContactReason> reasonFor(int oldestDaysOverdue, int installmentsOverdue) {
        boolean byDays = oldestDaysOverdue >= daysOverdue;
        boolean byInstallments = installmentsOverdue >= overdueInstallments;
        if (byDays && byInstallments) {
            return Optional.of(OverdueContactReason.DAYS_AND_INSTALLMENTS);
        }
        if (byDays) {
            return Optional.of(OverdueContactReason.DAYS_OVERDUE);
        }
        return byInstallments ? Optional.of(OverdueContactReason.OVERDUE_INSTALLMENTS) : Optional.empty();
    }
}
