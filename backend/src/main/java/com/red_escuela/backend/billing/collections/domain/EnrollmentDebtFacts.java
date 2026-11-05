package com.red_escuela.backend.billing.collections.domain;

public record EnrollmentDebtFacts(int overdueMonthlyInstallments, int overdueEnrollmentFees, int overdueDebitNotes, int overdueOtherInvoices) {

    public EnrollmentDebtFacts {
        if (overdueMonthlyInstallments < 0 || overdueEnrollmentFees < 0 || overdueDebitNotes < 0 || overdueOtherInvoices < 0) {
            throw new IllegalArgumentException("The overdue counts must not be negative");
        }
    }

    public boolean hasAnyOverdueInvoice() {
        return overdueMonthlyInstallments + overdueEnrollmentFees + overdueDebitNotes + overdueOtherInvoices > 0;
    }
}
