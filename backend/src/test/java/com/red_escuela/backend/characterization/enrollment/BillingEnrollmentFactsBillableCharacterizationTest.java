package com.red_escuela.backend.characterization.enrollment;

import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.support.PaymentStandingSeed;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFactsFinder;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class BillingEnrollmentFactsBillableCharacterizationTest extends WithdrawalP2CharacterizationSupport {

    @Autowired
    private BillingEnrollmentFactsFinder factsFinder;

    private boolean activeFactOf(ProgramEnrollmentStatus status, EnrollmentPaymentStatus standing) {
        ProgramEnrollment enrollment = persistProgramEnrollment(persistStudent(), persistProgram(), status);
        entityManager.flush();
        if (standing != null) {
            PaymentStandingSeed.seedProgramEnrollmentStanding(jdbc, enrollment.getId(), standing);
        }
        return factsFinder.findProgramEnrollmentFacts(enrollment.getId()).map(BillingEnrollmentFacts::active).orElseThrow();
    }

    @Test
    void enrolledProgramEnrollmentWithoutStandingIsActiveForBilling() {
        assertThat(activeFactOf(ProgramEnrollmentStatus.ENROLLED, null)).isTrue();
    }

    @Test
    void enrolledProgramEnrollmentUpToDateOrOverdueIsActiveForBilling() {
        assertThat(activeFactOf(ProgramEnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.UP_TO_DATE)).isTrue();
        assertThat(activeFactOf(ProgramEnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.OVERDUE)).isTrue();
    }

    @Test
    void suspendedOrWithdrawnForNonpaymentProgramEnrollmentIsNotActiveForBilling() {
        assertThat(activeFactOf(ProgramEnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.SUSPENDED)).isFalse();
        assertThat(activeFactOf(ProgramEnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT)).isFalse();
    }

    @Test
    void programEnrollmentThatIsNotEnrolledIsNotActiveForBillingEvenWithGoodStanding() {
        assertThat(activeFactOf(ProgramEnrollmentStatus.PAUSED, EnrollmentPaymentStatus.UP_TO_DATE)).isFalse();
        assertThat(activeFactOf(ProgramEnrollmentStatus.COMPLETED, null)).isFalse();
    }
}
