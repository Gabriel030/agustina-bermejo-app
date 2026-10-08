package com.red_escuela.backend.billing.planning.service;

public enum CourseInstallmentMode {
    MONTHLY("ISSUE_INSTALLMENT", false),
    UPFRONT("ISSUE_UPFRONT_INSTALLMENT", true);

    private final String decisionCode;
    private final boolean upfront;

    CourseInstallmentMode(String decisionCode, boolean upfront) {
        this.decisionCode = decisionCode;
        this.upfront = upfront;
    }

    public String decisionCode() {
        return decisionCode;
    }

    public boolean upfront() {
        return upfront;
    }
}
