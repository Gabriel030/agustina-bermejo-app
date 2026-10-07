package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.BillingConfigurationAlert;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
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

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class InvoiceSequenceTest extends AbstractInvoiceSequenceTest {

    @Autowired
    private BillingConfigurationAlertService alertService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private BillingTestTrigger invoiceGenerationService;

    private static final YearMonth JAN_2026 = YearMonth.of(2026, 1);
    private static final YearMonth DEC_2027 = YearMonth.of(2027, 12);

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
        assertThat(invoices(enrollment, FeeType.ENROLLMENT_FEE).get(0).getBaseAmount()).isEqualTo(ars(5000));
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
        transactionTemplate.executeWithoutResult(s -> entityManager.find(com.red_escuela.backend.billing.entity.ProgramCohort.class, cohort)
                .setEntryFeeDiscount(com.red_escuela.backend.shared.domain.Percentage.ofPoints(java.math.BigDecimal.ZERO)));
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
        assertThat(installments.stream().filter(i -> i.getPeriod().isBefore(YearMonth.of(2026, 6)))).allMatch(i -> i.getBaseAmount().equals(ars(10000)));
        assertThat(installments.stream().filter(i -> !i.getPeriod().isBefore(YearMonth.of(2026, 6)))).allMatch(i -> i.getBaseAmount().equals(ars(12000)));
        assertThat(invoices(enrollment, FeeType.ENROLLMENT_FEE).get(0).getBaseAmount()).isEqualTo(ars(5000));
        assertThat(invoices(enrollment, FeeType.ENROLLMENT_FEE).get(1).getBaseAmount()).isEqualTo(ars(7000));
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

    @Test
    void upfrontStudentsAreChargedTheEnrollmentFeeAndTheInstallmentsOfTheirScopeUpFront() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        today(date(2026, 1, 10));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.ANNUAL_UPFRONT, JAN_2026, date(2026, 1, 10));

        runEveryMonth(JAN_2026, YearMonth.of(2026, 6), () -> assertInvariants(enrollment, y26));

        assertThat(sequence(enrollment)).containsExactly("M2026-02", "C2026-03", "C2026-04", "C2026-05", "C2026-06", "C2026-07", "C2026-08", "C2026-09", "C2026-10");
    }

    @Test
    @Tag("D-016")
    void studentWhoOnlyOwesFinalExamsIsNotChargedUnlessTheYearIsConfiguredToRequireIt() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        today(date(2026, 1, 10));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 10));
        var term = newCuatrimestre(2026, 3, date(2026, 2, 1), date(2026, 3, 10), "1C");
        addSectionEnrollment(enrollment, career, term, EnrollmentStatus.REGULAR);

        runEveryMonth(JAN_2026, YearMonth.of(2026, 3), () -> {
        });
        assertThat(sequence(enrollment)).isEmpty();

        configureYear(y26, template -> template.setRequireEnrollmentFeeForFinalExamOnlyStudents(true));
        today(date(2026, 2, 2));
        job.run();
        today(date(2026, 3, 2));
        job.run();
        assertThat(sequence(enrollment)).containsExactly("M2026-02");
    }

    @Test
    @Tag("D-022")
    @Tag("D-024")
    void enrollmentFeeIsEmittedFifteenDaysBeforeTheEnrollmentWindowOpensWithDueDateAtWindowCloseAndNotRetroactively() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        newSection(career, newCuatrimestre(2027, 3, date(2027, 1, 25), date(2027, 3, 12), "1C"));
        today(date(2026, 12, 1));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2026, 12), date(2026, 12, 1));

        today(date(2026, 11, 2));
        job.run();
        assertThat(sequence(enrollment)).isEmpty();
        today(date(2026, 12, 2));
        job.run();

        List<Invoice> fees = invoices(enrollment, FeeType.ENROLLMENT_FEE);
        assertThat(fees).hasSize(1);
        assertThat(fees.get(0).getPeriod()).isEqualTo(YearMonth.of(2027, 2));
        assertThat(fees.get(0).getDueDate()).isEqualTo(date(2027, 3, 12));
        assertInvariants(enrollment, y27);
    }

    @Test
    @Tag("D-026")
    void windowThatClosedBeforeTheYearWasConfiguredIsNotChargedRetroactively() {
        Career career = newCareer();
        newYear(career, 2027, 10, 1, 2, null);
        newSection(career, newCuatrimestre(2027, 3, date(2027, 1, 25), date(2027, 3, 12), "1C"));
        today(date(2026, 12, 1));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2026, 12), date(2026, 12, 1));

        today(date(2027, 4, 2));
        job.run();

        assertThat(invoices(enrollment, FeeType.ENROLLMENT_FEE)).isEmpty();
    }

    @Test
    @Tag("D-026")
    void settingUpTheYearEmitsImmediatelyToWhoeverHasAnOpenWindowWithoutEnrollmentFee() {
        Career career = newCareer();
        newSection(career, newCuatrimestre(2027, 3, date(2027, 1, 25), date(2027, 3, 12), "1C"));
        today(date(2026, 12, 1));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2026, 12), date(2026, 12, 1));
        today(date(2027, 2, 10));
        newYear(career, 2027, 10, 1, 2, null);

        publishYearBillingConfigured(career);

        await().untilAsserted(() -> assertThat(periods(enrollment, FeeType.ENROLLMENT_FEE)).containsExactly(YearMonth.of(2027, 2)));
        publishYearBillingConfigured(career);
        await().during(java.time.Duration.ofSeconds(1)).untilAsserted(() -> assertThat(periods(enrollment, FeeType.ENROLLMENT_FEE)).hasSize(1));
    }

    @Test
    @Tag("D-050")
    void mixedFleetKeepsAllInvariantsOverTwoLectiveYears() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 2, 2, 7);
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long marchCohort = newCohort(y26, CohortIntake.FIRST, date(2026, 3, 1));
        Long augustCohort = newCohort(y26, CohortIntake.SECOND, date(2026, 8, 3));
        today(date(2026, 1, 5));
        Long a = newEnrollment(career, marchCohort, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 5));
        Long b = newEnrollment(career, marchCohort, PaymentPlanCadence.ANNUAL_UPFRONT, JAN_2026, date(2026, 1, 5));
        Long c = newEnrollment(career, augustCohort, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 5));
        issueEntryFee(a);
        issueEntryFee(b);
        issueEntryFee(c);

        runEveryMonth(JAN_2026, DEC_2027, () -> {
            assertInvariants(a, y26, y27);
            assertInvariants(b, y26, y27);
            assertInvariants(c, y26, y27);
        });

        assertThat(periods(b, FeeType.MONTHLY_INSTALLMENT)).startsWith(YearMonth.of(2026, 3), YearMonth.of(2026, 4));
        assertThat(periods(b, FeeType.MONTHLY_INSTALLMENT)).contains(YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 3));
        assertThat(periods(a, FeeType.ENROLLMENT_FEE)).containsExactly(
                YearMonth.of(2026, 3), YearMonth.of(2026, 7), YearMonth.of(2027, 2));
        assertThat(periods(c, FeeType.ENROLLMENT_FEE)).containsExactly(
                YearMonth.of(2026, 8), YearMonth.of(2027, 2));
    }

    @Test
    @Tag("D-036")
    void alreadyIssuedChargesAreNeverTouchedWhenTheConfigurationChanges() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        today(date(2026, 1, 10));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 10));
        runEveryMonth(JAN_2026, YearMonth.of(2026, 4), () -> {
        });
        List<String> before = sequence(enrollment);
        markInvoices(enrollment, FeeType.MONTHLY_INSTALLMENT, YearMonth.of(2026, 3), InvoiceStatus.PAID);

        configureYear(y26, template -> template.configureEnrollmentFees(2, 2, 7));
        runEveryMonth(YearMonth.of(2026, 4), YearMonth.of(2026, 4), () -> {
        });

        assertThat(sequence(enrollment)).isEqualTo(before);
        assertThat(invoices(enrollment, FeeType.MONTHLY_INSTALLMENT).get(0).getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    @Tag("D-005")
    void firstAndThirdYearStudentsOfTheSameProgramPayTheSameInstallment() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y27, CohortIntake.FIRST, date(2027, 3, 1));
        today(date(2027, 1, 15));
        Long firstYear = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 1), date(2027, 1, 15));
        Long thirdYear = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 1), date(2025, 3, 1));
        issueEntryFee(firstYear);

        runEveryMonth(YearMonth.of(2027, 1), YearMonth.of(2027, 4), () -> { });

        Invoice firstYearApril = invoices(firstYear, FeeType.MONTHLY_INSTALLMENT).stream().filter(i -> i.getPeriod().equals(YearMonth.of(2027, 4))).findFirst().orElseThrow();
        Invoice thirdYearApril = invoices(thirdYear, FeeType.MONTHLY_INSTALLMENT).stream().filter(i -> i.getPeriod().equals(YearMonth.of(2027, 4))).findFirst().orElseThrow();
        assertThat(firstYearApril.getBaseAmount()).isEqualTo(ars(10000));
        assertThat(thirdYearApril.getBaseAmount()).isEqualTo(firstYearApril.getBaseAmount());
    }

    private List<BillingConfigurationAlert> openAlerts(Career career) {
        return alertService.listOpen().stream().filter(a -> a.getProgram().getId().equals(career.programId())).toList();
    }

    private void publishYearBillingConfigured(Career career) {
        transactionTemplate.executeWithoutResult(s -> eventPublisher.publishEvent(YearBillingConfiguredEvent.configuredFor(career.programId())));
    }
}
