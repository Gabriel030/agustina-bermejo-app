package com.red_escuela.backend.billing.entity;

import com.red_escuela.backend.billing.invoice.domain.FeeType;

public enum ScholarshipScope {
    INSTALLMENTS, ENROLLMENT_FEE, BOTH;

    public boolean covers(FeeType feeType) {
        return switch (this) {
            case INSTALLMENTS -> feeType == FeeType.MONTHLY_INSTALLMENT;
            case ENROLLMENT_FEE -> feeType == FeeType.ENROLLMENT_FEE;
            case BOTH -> feeType == FeeType.MONTHLY_INSTALLMENT || feeType == FeeType.ENROLLMENT_FEE;
        };
    }
}
