package com.red_escuela.backend.billing.collections.domain;

import java.time.LocalDate;

public record EnrollmentDebtFacts(int overdueMonthlyInstallments, LocalDate oldestOverdueInstallmentDueDate, int overdueEnrollmentFees,
                                  int overdueDebitNotes, int overdueOtherInvoices, LocalDate monthCloseCutoff) {

    public EnrollmentDebtFacts {
        if (overdueMonthlyInstallments < 0 || overdueEnrollmentFees < 0 || overdueDebitNotes < 0 || overdueOtherInvoices < 0) {
            throw new IllegalArgumentException("The overdue counts must not be negative");
        }
        if ((overdueMonthlyInstallments > 0) != (oldestOverdueInstallmentDueDate != null)) {
            throw new IllegalArgumentException("The oldest overdue installment date is present exactly when there are overdue installments");
        }
    }

    public boolean hasAnyOverdueInvoice() {
        return overdueMonthlyInstallments + overdueEnrollmentFees + overdueDebitNotes + overdueOtherInvoices > 0;
    }

    public boolean isMonthClose() {
        return monthCloseCutoff != null;
    }
}
