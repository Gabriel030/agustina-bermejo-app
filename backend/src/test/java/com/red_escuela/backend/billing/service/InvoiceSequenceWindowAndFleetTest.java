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

class InvoiceSequenceWindowAndFleetTest extends AbstractInvoiceSequenceScenarioTest {

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

        configureYear(y26, template -> com.red_escuela.backend.billing.planning.TestTemplateTerms.setRequireEnrollmentFeeForFinalExamOnlyStudents(template, true));
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

        assertThat(periods(b, FeeType.MONTHLY_INSTALLMENT)).doesNotContain(YearMonth.of(2026, 3), YearMonth.of(2026, 4));
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
        markInvoices(enrollment, FeeType.MONTHLY_INSTALLMENT, YearMonth.of(2026, 3), InvoicePaymentStatus.PAID);

        configureYear(y26, template -> com.red_escuela.backend.billing.planning.TestTemplateTerms.configureEnrollmentFees(template, 2, 2, 7));
        runEveryMonth(YearMonth.of(2026, 4), YearMonth.of(2026, 4), () -> {
        });

        assertThat(sequence(enrollment)).isEqualTo(before);
        assertThatInvoice(invoices(enrollment, FeeType.MONTHLY_INSTALLMENT).get(0)).isPaid();
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
        assertThat(firstYearApril.getAmounts().linesTotal().value()).isEqualTo(ars(10000));
        assertThat(thirdYearApril.getAmounts().linesTotal().value()).isEqualTo(firstYearApril.getAmounts().linesTotal().value());
    }
}
