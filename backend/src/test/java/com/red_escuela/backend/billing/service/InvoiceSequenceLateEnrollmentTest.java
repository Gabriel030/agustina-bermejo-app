package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.BillingConfigurationAlert;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.time.YearMonth;
import java.util.List;

import static com.red_escuela.backend.billing.support.InvoiceAssertions.assertThatInvoice;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class InvoiceSequenceLateEnrollmentTest extends AbstractInvoiceSequenceScenarioTest {

    @Test
    @Tag("D-049")
    void lateEnrollmentThroughThePaymentPlanSelectionIsChargedTheCurrentMonthButNotThePastOnes() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        Long cohort = newCohort(y26, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 4, 20));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2026, 4), date(2026, 4, 20));
        issueEntryFee(enrollment);

        transactionTemplate.executeWithoutResult(s -> invoiceGenerationService.issueInvoicesForNewPaymentPlan(
                entityManager.find(ProgramEnrollment.class, enrollment), YearMonth.of(2026, 4), date(2026, 4, 20)));
        today(date(2026, 5, 1));
        job.run();
        today(date(2026, 5, 2));
        job.run();

        assertThat(sequence(enrollment)).isEqualTo(expected("M2026-03", "C2026-04", "C2026-05"));
        assertInvariants(enrollment, y26);
    }

    @Test
    @Tag("D-049")
    void reactivationAfterSuspensionEmitsTheCurrentMonthChargesInTheSameOperation() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        today(date(2026, 3, 10));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 10));
        setStanding(enrollment, EnrollmentPaymentStatus.SUSPENDED);
        today(date(2026, 5, 1));
        job.run();
        assertThat(sequence(enrollment)).isEmpty();

        setStanding(enrollment, EnrollmentPaymentStatus.UP_TO_DATE);
        today(date(2026, 5, 20));
        transactionTemplate.executeWithoutResult(s -> eventPublisher.publishEvent(EnrollmentSuspensionLiftedEvent.liftedFor(EnrollmentSuspensionLiftedEvent.EnrollmentType.PROGRAM, enrollment, YearMonth.of(2026, 3), YearMonth.of(2026, 5))));

        await().untilAsserted(() -> assertThat(sequence(enrollment)).isEqualTo(expected("M2026-02", "C2026-05")));
        Invoice lateFee = invoices(enrollment, FeeType.ENROLLMENT_FEE).get(0);
        assertThat(lateFee.getDueDate()).isEqualTo(date(2026, 5, 30));
        assertInvariants(enrollment, y26);
    }

    @Test
    @Tag("D-006")
    void withoutAConfiguredLectiveYearNothingIsEmittedAnAlertIsRaisedAndConfiguringItCompletesThePending() {
        Career career = newCareer();
        newYear(career, 2026, 10, 1, 2, null);
        today(date(2026, 11, 10));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2026, 11), date(2026, 11, 10));

        today(date(2027, 2, 1));
        job.run();

        assertThat(sequence(enrollment)).isEmpty();
        assertThat(openAlerts(career)).hasSize(1);
        assertThat(openAlerts(career).get(0).getPeriod()).isEqualTo(YearMonth.of(2027, 2));
        assertThat(openAlerts(career).get(0).getAffectedEnrollments()).isEqualTo(1);

        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        publishYearBillingConfigured(career);

        await().untilAsserted(() -> {
            assertThat(sequence(enrollment)).containsExactly("M2027-02");
            assertThat(openAlerts(career)).isEmpty();
        });
        today(date(2027, 3, 1));
        job.run();
        assertThat(sequence(enrollment)).containsExactly("M2027-02", "C2027-03");
        assertInvariants(enrollment, y27);
    }

    @Test
    @Tag("D-033")
    void aMonthInsideTheInstallmentRangeIssuesTwoInvoicesAndOneOutsideIssuesOnlyTheEnrollmentFee() {
        Career career = newCareer();
        Year inside = newYear(career, 2026, 10, 1, 3, null);
        today(date(2026, 1, 10));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 10));

        runEveryMonth(JAN_2026, YearMonth.of(2026, 4), () -> assertInvariants(enrollment, inside));

        assertThat(sequence(enrollment)).isEqualTo(expected("M2026-03", "C2026-03", "C2026-04"));
        Career other = newCareer();
        Year outside = newYear(other, 2026, 10, 1, 2, null);
        Long second = newEnrollment(other, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 10));
        runEveryMonth(JAN_2026, YearMonth.of(2026, 3), () -> assertInvariants(second, outside));
        assertThat(sequence(second)).isEqualTo(expected("M2026-02", "C2026-03"));
    }

    @Test
    @Tag("D-034")
    void installmentsNeverExceedTheLectiveYearCap() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 4, 1, 2, null);
        today(date(2026, 1, 10));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 10));

        runEveryMonth(JAN_2026, YearMonth.of(2026, 12), () -> assertInvariants(enrollment, y26));

        assertThat(periods(enrollment, FeeType.MONTHLY_INSTALLMENT)).hasSize(4);
    }

    @Test
    @Tag("D-007")
    void onlyActiveEnrollmentsAreBilledPausedIncluded() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        today(date(2026, 1, 10));
        Long active = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 10));
        Long paused = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 10));
        Long suspended = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 10));
        Long withdrawnForNonPayment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 10));
        Long withdrawn = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 10));
        Long completed = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 10));
        setStatus(paused, ProgramEnrollmentStatus.PAUSED);
        setStanding(suspended, EnrollmentPaymentStatus.SUSPENDED);
        setStanding(withdrawnForNonPayment, EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
        setStatus(withdrawn, ProgramEnrollmentStatus.WITHDRAWN);
        setStatus(completed, ProgramEnrollmentStatus.COMPLETED);

        runEveryMonth(JAN_2026, YearMonth.of(2026, 3), () -> {
        });

        assertThat(sequence(active)).isEqualTo(expected("M2026-02", "C2026-03"));
        assertThat(sequence(paused)).isEmpty();
        for (Long id : List.of(suspended, withdrawnForNonPayment, withdrawn, completed)) {
            assertThat(sequence(id)).isEmpty();
        }
        assertInvariants(active, y26);
    }
}
