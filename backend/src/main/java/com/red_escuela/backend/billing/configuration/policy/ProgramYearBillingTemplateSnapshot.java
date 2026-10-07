package com.red_escuela.backend.billing.configuration.policy;

public record ProgramYearBillingTemplateSnapshot(int enrollmentFeeCount, int firstEnrollmentFeeMonth, Integer secondEnrollmentFeeMonth,
                                                 Integer recurringFeeInvoicesCount, int enrollmentFeeLeadMonths) {

    private static final int DEFAULT_ENROLLMENT_FEE_LEAD_MONTHS = 1;

    public ProgramYearBillingTemplateSnapshot(int enrollmentFeeCount, int firstEnrollmentFeeMonth, Integer secondEnrollmentFeeMonth,
                                              Integer recurringFeeInvoicesCount) {
        this(enrollmentFeeCount, firstEnrollmentFeeMonth, secondEnrollmentFeeMonth, recurringFeeInvoicesCount, DEFAULT_ENROLLMENT_FEE_LEAD_MONTHS);
    }

    public Integer enrollmentFeeMonthOfSlot(int slot) {
        return slot == 1 ? firstEnrollmentFeeMonth : secondEnrollmentFeeMonth;
    }
}
