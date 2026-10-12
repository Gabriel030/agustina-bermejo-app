package com.red_escuela.backend.billing.invoice.domain;

public enum InvoiceIssuingReason {
    MONTHLY_RUN,
    PAYMENT_PLAN_SELECTION,
    REACTIVATION,
    ENROLLMENT_FEE_ON_DEMAND,
    YEAR_CONFIGURATION_CHANGE,
    ANNUAL_UPFRONT_PAYMENT,
    PROGRAM_ADMISSION,
    COURSE_MONTHLY_RUN,
    COURSE_ADMISSION,
    AD_HOC_CHARGE,
    LATE_FEE;

    public boolean isProgramAdmission() {
        return this == PROGRAM_ADMISSION;
    }

    public boolean isAdmission() {
        return this == PROGRAM_ADMISSION || this == COURSE_ADMISSION;
    }
}
