package com.red_escuela.backend.billing.configuration.policy;

import java.time.LocalDate;

public record ProgramYearBillingTemplateSnapshot(int enrollmentFeeCount, int firstEnrollmentFeeMonth, Integer secondEnrollmentFeeMonth,
                                                 Integer recurringFeeInvoicesCount, int enrollmentFeeLeadMonths, LocalDate advanceEnrollmentFeeOpensOn) {

    private static final int DEFAULT_ENROLLMENT_FEE_LEAD_MONTHS = 1;

    public ProgramYearBillingTemplateSnapshot(int enrollmentFeeCount, int firstEnrollmentFeeMonth, Integer secondEnrollmentFeeMonth,
                                              Integer recurringFeeInvoicesCount) {
        this(enrollmentFeeCount, firstEnrollmentFeeMonth, secondEnrollmentFeeMonth, recurringFeeInvoicesCount, DEFAULT_ENROLLMENT_FEE_LEAD_MONTHS);
    }

    public ProgramYearBillingTemplateSnapshot(int enrollmentFeeCount, int firstEnrollmentFeeMonth, Integer secondEnrollmentFeeMonth,
                                              Integer recurringFeeInvoicesCount, int enrollmentFeeLeadMonths) {
        this(enrollmentFeeCount, firstEnrollmentFeeMonth, secondEnrollmentFeeMonth, recurringFeeInvoicesCount, enrollmentFeeLeadMonths, null);
    }

    public Integer enrollmentFeeMonthOfSlot(int slot) {
        return slot == 1 ? firstEnrollmentFeeMonth : secondEnrollmentFeeMonth;
    }
}
