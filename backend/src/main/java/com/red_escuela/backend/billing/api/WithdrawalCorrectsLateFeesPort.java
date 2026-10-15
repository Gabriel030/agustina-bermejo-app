package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.events.EnrollmentWithdrawnForBillingEvent;
import com.red_escuela.backend.enrollment.api.EnrollmentWithdrawalBillingPort;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class WithdrawalCorrectsLateFeesPort implements EnrollmentWithdrawalBillingPort {

    private final ApplicationEventPublisher eventPublisher;

    public WithdrawalCorrectsLateFeesPort(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void registerWithdrawalForBilling(WithdrawnEnrollmentFacts facts) {
        eventPublisher.publishEvent(EnrollmentWithdrawnForBillingEvent.withdrawnFor(facts.studentId(), facts.programEnrollmentId(), facts.courseEnrollmentId(),
                facts.effectiveDate(), facts.registeredOn(), facts.cause().name()));
    }
}
