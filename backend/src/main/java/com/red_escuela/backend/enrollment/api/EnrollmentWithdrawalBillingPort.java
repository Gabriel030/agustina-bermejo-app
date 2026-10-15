package com.red_escuela.backend.enrollment.api;

public interface EnrollmentWithdrawalBillingPort {
    void registerWithdrawalForBilling(WithdrawnEnrollmentFacts facts);
}
