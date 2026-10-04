package com.red_escuela.backend.billing.entity;

public enum ScholarshipScope {
    INSTALLMENTS, ENROLLMENT_FEE, BOTH;

    public boolean covers(FeeType feeType) {
        return switch (this) {
            case INSTALLMENTS -> feeType == FeeType.RECURRING_FEE;
            case ENROLLMENT_FEE -> feeType == FeeType.ENROLLMENT_FEE;
            case BOTH -> feeType == FeeType.RECURRING_FEE || feeType == FeeType.ENROLLMENT_FEE;
        };
    }
}
