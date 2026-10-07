package com.red_escuela.backend.billing.configuration.policy;

public record ProgramYearBillingTemplateSnapshot(int enrollmentFeeCount, int firstEnrollmentFeeMonth,
                                                 Integer secondEnrollmentFeeMonth, Integer recurringFeeInvoicesCount) {

    public Integer enrollmentFeeMonthOfSlot(int slot) {
        return slot == 1 ? firstEnrollmentFeeMonth : secondEnrollmentFeeMonth;
    }
}
