package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.EnrollmentWithdrawalBillingPort;
import com.red_escuela.backend.enrollment.api.WithdrawalCause;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import com.red_escuela.backend.enrollment.events.ProgramEnrollmentGraduatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class GraduationBillingNotifier {

    private final EnrollmentWithdrawalBillingPort withdrawalBillingPort;

    public GraduationBillingNotifier(EnrollmentWithdrawalBillingPort withdrawalBillingPort) {
        this.withdrawalBillingPort = withdrawalBillingPort;
    }

    @EventListener
    public void registerGraduationForBilling(ProgramEnrollmentGraduatedEvent event) {
        withdrawalBillingPort.registerWithdrawalForBilling(WithdrawnEnrollmentFacts.ofProgramEnrollment(
                event.studentId(), event.programEnrollmentId(), event.effectiveDate(), event.effectiveDate(), WithdrawalCause.GRADUATION));
    }
}
