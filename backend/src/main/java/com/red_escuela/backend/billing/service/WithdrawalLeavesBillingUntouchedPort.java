package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.api.EnrollmentWithdrawalBillingPort;
import com.red_escuela.backend.billing.api.WithdrawnEnrollmentFacts;
import org.springframework.stereotype.Service;

@Service
public class WithdrawalLeavesBillingUntouchedPort implements EnrollmentWithdrawalBillingPort {

    @Override
    public void registerWithdrawalForBilling(WithdrawnEnrollmentFacts facts) {
    }
}
