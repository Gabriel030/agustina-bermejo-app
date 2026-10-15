package com.red_escuela.backend.billing.api;

public interface EnrollmentWithdrawalBillingPort {
    void registerWithdrawalForBilling(WithdrawnEnrollmentFacts facts);
}
