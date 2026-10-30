package com.red_escuela.backend.billing.entity;

public enum EnrollmentPaymentStatus {
    UP_TO_DATE, OVERDUE, SUSPENDED, WITHDRAWN_FOR_NONPAYMENT;

    public boolean stopsBilling() {
        return this == SUSPENDED || this == WITHDRAWN_FOR_NONPAYMENT;
    }
}
