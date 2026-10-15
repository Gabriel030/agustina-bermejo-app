package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.enrollment.api.EnrollmentWithdrawalBillingPort;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import org.springframework.stereotype.Service;

@Service
public class WithdrawalLeavesBillingUntouchedPort implements EnrollmentWithdrawalBillingPort {

    @Override
    public void registerWithdrawalForBilling(WithdrawnEnrollmentFacts facts) {
    }
}
