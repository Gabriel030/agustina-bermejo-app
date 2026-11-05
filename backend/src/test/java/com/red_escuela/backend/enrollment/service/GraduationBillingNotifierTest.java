package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.EnrollmentWithdrawalBillingPort;
import com.red_escuela.backend.enrollment.api.WithdrawalCause;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import com.red_escuela.backend.enrollment.events.ProgramEnrollmentGraduatedEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Tag("D-236")
@Tag("D-296")
class GraduationBillingNotifierTest {

    @Test
    void graduationEventIsRegisteredInBillingWithTheGraduationCause() {
        EnrollmentWithdrawalBillingPort port = mock(EnrollmentWithdrawalBillingPort.class);
        GraduationBillingNotifier notifier = new GraduationBillingNotifier(port);

        notifier.registerGraduationForBilling(ProgramEnrollmentGraduatedEvent.graduatedOn(41L, 10L, LocalDate.of(2027, 10, 20)));

        verify(port).registerWithdrawalForBilling(WithdrawnEnrollmentFacts.ofProgramEnrollment(41L, 10L, LocalDate.of(2027, 10, 20), LocalDate.of(2027, 10, 20), WithdrawalCause.GRADUATION));
    }
}
