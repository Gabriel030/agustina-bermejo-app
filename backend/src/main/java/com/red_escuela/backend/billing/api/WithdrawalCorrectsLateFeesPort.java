package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.discount.service.StudentDiscountEnrollmentEndService;
import com.red_escuela.backend.billing.events.EnrollmentWithdrawnForBillingEvent;
import com.red_escuela.backend.billing.note.service.LaterChargesCancellationOnWithdrawal;
import com.red_escuela.backend.enrollment.api.EnrollmentWithdrawalBillingPort;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class WithdrawalCorrectsLateFeesPort implements EnrollmentWithdrawalBillingPort {

    private final ApplicationEventPublisher eventPublisher;
    private final LaterChargesCancellationOnWithdrawal laterChargesCancellation;
    private final StudentDiscountEnrollmentEndService discountEnrollmentEnd;

    public WithdrawalCorrectsLateFeesPort(ApplicationEventPublisher eventPublisher, LaterChargesCancellationOnWithdrawal laterChargesCancellation,
                                          StudentDiscountEnrollmentEndService discountEnrollmentEnd) {
        this.eventPublisher = eventPublisher;
        this.laterChargesCancellation = laterChargesCancellation;
        this.discountEnrollmentEnd = discountEnrollmentEnd;
    }

    @Override
    public void registerWithdrawalForBilling(WithdrawnEnrollmentFacts facts) {
        laterChargesCancellation.cancelLaterChargesOf(facts);
        discountEnrollmentEnd.endActiveDiscountsOf(facts);
        eventPublisher.publishEvent(EnrollmentWithdrawnForBillingEvent.withdrawnFor(facts.studentId(), facts.programEnrollmentId(), facts.courseEnrollmentId(),
                facts.effectiveDate(), facts.registeredOn(), facts.cause().name()));
    }
}
