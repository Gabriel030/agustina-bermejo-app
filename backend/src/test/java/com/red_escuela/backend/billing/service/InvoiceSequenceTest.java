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

class InvoiceSequenceTest extends AbstractInvoiceSequenceScenarioTest {

    @Test
    void marchIntakeWithOneEnrollmentFeeTracksTwoLectiveYearsMonthByMonth() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y26, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 1, 15));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 15));
        issueEntryFee(enrollment);

        runEveryMonth(JAN_2026, DEC_2027, () -> assertInvariants(enrollment, y26, y27));

        assertThat(sequence(enrollment)).isEqualTo(expected("M2026-03", "C2026-03..2026-12", "M2027-02", "C2027-03..2027-12"));
        assertThat(invoices(enrollment, FeeType.ENROLLMENT_FEE).get(0).getAmounts().linesTotal().value()).isEqualTo(ars(5000));
    }

    @Test
    void augustIntakePaysTheDiscountedEntryFeeWhichCountsForTheCycleAndTheNextYearEntersWithTheNewYearConfiguration() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y26, CohortIntake.SECOND, date(2026, 8, 3));
        today(date(2026, 7, 20));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2026, 7), date(2026, 7, 20));
        issueEntryFee(enrollment);

        runEveryMonth(YearMonth.of(2026, 7), DEC_2027, () -> assertInvariants(enrollment, y26, y27));

        assertThat(sequence(enrollment)).isEqualTo(expected("M2026-08", "C2026-08..2026-12", "M2027-02", "C2027-03..2027-12"));
        Invoice entryFee = invoices(enrollment, FeeType.ENROLLMENT_FEE).get(0);
        assertThat(amountDue(entryFee).amount().compareTo(ars(2500).amount())).isZero();
    }

    @Test
    @Tag("D-018")
    void entryFeeDiscountIsConfigurableAndZeroMeansNoDiscount() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        Long cohort = newCohort(y26, CohortIntake.SECOND, date(2026, 8, 3));
        transactionTemplate.executeWithoutResult(s -> com.red_escuela.backend.billing.planning.TestTemplateTerms.publishCohortEntryDiscount(entityManager,
                entityManager.find(com.red_escuela.backend.billing.entity.ProgramCohort.class, cohort), com.red_escuela.backend.shared.domain.Percentage.ofPoints(java.math.BigDecimal.ZERO)));
        today(date(2026, 7, 20));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2026, 7), date(2026, 7, 20));
        issueEntryFee(enrollment);

        Invoice entryFee = invoices(enrollment, FeeType.ENROLLMENT_FEE).get(0);
        assertThat(amountDue(entryFee).amount().compareTo(ars(5000).amount())).isZero();
    }

    @Test
    @Tag("D-002")
    void twoEnrollmentFeesMarchIntakeEmitsOnePerSemesterAndTheEntryFeeCountsAsTheFirst() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 2, 2, 7);
        Year y27 = newYear(career, 2027, 10, 2, 2, 7);
        Long cohort = newCohort(y26, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 1, 15));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 15));
        issueEntryFee(enrollment);

        runEveryMonth(JAN_2026, DEC_2027, () -> assertInvariants(enrollment, y26, y27));

        assertThat(sequence(enrollment)).isEqualTo(expected(
                "M2026-03", "C2026-03..2026-06", "M2026-07", "C2026-07..2026-12", "M2027-02", "C2027-03..2027-06", "M2027-07", "C2027-07..2027-12"));
    }

    @Test
    @Tag("D-020")
    void twoEnrollmentFeesAugustIntakeEntryFeeCoversTheSecondSemesterSoJulyIsNotCharged() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 2, 2, 7);
        Year y27 = newYear(career, 2027, 10, 2, 2, 7);
        Long cohort = newCohort(y26, CohortIntake.SECOND, date(2026, 8, 3));
        today(date(2026, 6, 20));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2026, 6), date(2026, 6, 20));
        issueEntryFee(enrollment);

        runEveryMonth(YearMonth.of(2026, 6), DEC_2027, () -> assertInvariants(enrollment, y26, y27));

        assertThat(sequence(enrollment)).isEqualTo(expected(
                "M2026-08", "C2026-08..2026-12", "M2027-02", "C2027-03..2027-06", "M2027-07", "C2027-07..2027-12"));
    }

    @Test
    @Tag("D-003")
    void secondYearStudentIsBilledAgainstTheNewLectiveYearConfigurationNotTheEntryCohort() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        Year y27 = newYear(career, 2027, 4, 1, 2, null);
        Long cohort = newCohort(y26, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 1, 15));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 15));
        issueEntryFee(enrollment);

        runEveryMonth(JAN_2026, DEC_2027, () -> assertInvariants(enrollment, y26, y27));

        assertThat(periods(enrollment, FeeType.MONTHLY_INSTALLMENT).stream().filter(p -> p.getYear() == 2027).toList())
                .containsExactly(YearMonth.of(2027, 3), YearMonth.of(2027, 4), YearMonth.of(2027, 5), YearMonth.of(2027, 6));
        assertThat(periods(enrollment, FeeType.MONTHLY_INSTALLMENT).stream().filter(p -> p.getYear() == 2026)).hasSize(10);
    }

    @Test
    @Tag("D-035")
    void priceChangeInTheMiddleOfTheYearOnlyAffectsWhatIsEmittedAfterTheChange() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y26, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 1, 15));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 15));
        issueEntryFee(enrollment);
        newPrice(career, FeeType.MONTHLY_INSTALLMENT, 12000, date(2026, 6, 1));
        newPrice(career, FeeType.ENROLLMENT_FEE, 7000, date(2027, 1, 15));

        runEveryMonth(JAN_2026, DEC_2027, () -> assertInvariants(enrollment, y26, y27));

        List<Invoice> installments = invoices(enrollment, FeeType.MONTHLY_INSTALLMENT);
        assertThat(installments.stream().filter(i -> i.getPeriod().isBefore(YearMonth.of(2026, 6)))).allMatch(i -> i.getAmounts().linesTotal().value().equals(ars(10000)));
        assertThat(installments.stream().filter(i -> !i.getPeriod().isBefore(YearMonth.of(2026, 6)))).allMatch(i -> i.getAmounts().linesTotal().value().equals(ars(12000)));
        assertThat(invoices(enrollment, FeeType.ENROLLMENT_FEE).get(0).getAmounts().linesTotal().value()).isEqualTo(ars(5000));
        assertThat(invoices(enrollment, FeeType.ENROLLMENT_FEE).get(1).getAmounts().linesTotal().value()).isEqualTo(ars(7000));
    }

    @Test
    void jobFailingOnDayOneIsCompletedOnDayTwoAndRepeatedRunsDoNotDuplicate() {
        Career career = newCareer(date(2027, 2, 2));
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        today(date(2026, 12, 10));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2026, 12), date(2026, 12, 10));

        today(date(2027, 2, 1));
        job.run();
        assertThat(invoices(enrollment, FeeType.ENROLLMENT_FEE)).isEmpty();

        today(date(2027, 2, 2));
        job.run();
        job.run();
        today(date(2027, 2, 3));
        job.run();

        assertThat(periods(enrollment, FeeType.ENROLLMENT_FEE)).containsExactly(YearMonth.of(2027, 2));
        assertInvariants(enrollment, y27);
    }

    @Test
    @Tag("D-055")
    void jobDoesNotEmitOnDaySixAndTheManualRunCompletesWhatIsPending() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        today(date(2026, 1, 10));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 10));

        today(date(2026, 2, 6));
        job.run();
        today(date(2026, 3, 6));
        job.run();
        assertThat(sequence(enrollment)).isEmpty();

        job.run(YearMonth.of(2026, 3));
        assertThat(sequence(enrollment)).containsExactly("C2026-03");
        assertInvariants(enrollment, y26);
    }
}
