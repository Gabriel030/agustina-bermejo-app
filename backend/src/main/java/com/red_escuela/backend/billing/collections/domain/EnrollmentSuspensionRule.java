package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;

import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record EnrollmentSuspensionRule(int minimumOverdueInstallments, int minimumDaysOverdue) {

    public EnrollmentSuspensionRule {
        if (minimumOverdueInstallments < 1 || minimumDaysOverdue < 0) {
            throw new IllegalArgumentException("A suspension needs at least one installment and a non-negative number of days");
        }
    }

    public static EnrollmentSuspensionRule from(LatePaymentTerms terms) {
        Objects.requireNonNull(terms, "terms");
        return new EnrollmentSuspensionRule(Math.max(1, terms.suspensionAfterInvoiceCount()), Math.max(0, terms.suspensionAfterDays()));
    }

    public boolean suspends(MonthCloseFacts closeFacts) {
        if (closeFacts.overdueInstallmentsAtCutoff() < minimumOverdueInstallments) {
            return false;
        }
        return minimumDaysOverdue == 0
                || ChronoUnit.DAYS.between(closeFacts.oldestOverdueInstallmentDueDate(), closeFacts.cutoff()) >= minimumDaysOverdue;
    }
}
