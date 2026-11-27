package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.academicstructure.domain.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Simulates month-by-month billing across two lective years to cross the December to January boundary")
class LectiveYearBoundaryInvoiceSequenceIT extends AbstractInvoiceSequenceScenarioTest {

    private ProgramEnrollmentStatus statusOf(Long enrollmentId) {
        return transactionTemplate.execute(status -> entityManager.find(ProgramEnrollment.class, enrollmentId).getStatus());
    }

    @Test
    void monthlyRunAcrossTheLectiveYearBoundaryBillsEachInstallmentToItsOwnYear() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        Year y27 = newYear(career, 2027, 4, 1, 2, null);
        Long cohort = newCohort(y26, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 1, 15));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 15));
        issueEntryFee(enrollment);
        newPrice(career, FeeType.MONTHLY_INSTALLMENT, 12000, date(2027, 1, 1));

        runEveryMonth(JAN_2026, YearMonth.of(2027, 7), () -> assertInvariants(enrollment, y26, y27));

        List<Invoice> installments = invoices(enrollment, FeeType.MONTHLY_INSTALLMENT);
        List<Invoice> of2026 = installments.stream().filter(i -> i.getPeriod().getYear() == 2026).toList();
        List<Invoice> of2027 = installments.stream().filter(i -> i.getPeriod().getYear() == 2027).toList();
        assertThat(of2026).hasSize(10).allMatch(i -> y26.templateId().equals(i.getTemplateId()));
        assertThat(of2027).hasSize(4).allMatch(i -> y27.templateId().equals(i.getTemplateId()));
        assertThat(periods(enrollment, FeeType.MONTHLY_INSTALLMENT)).doesNotContain(YearMonth.of(2027, 1), YearMonth.of(2027, 2));
        assertThat(of2026).extracting(i -> i.getAmounts().linesTotal().value()).containsOnly(ars(10000));
        assertThat(of2027).extracting(i -> i.getAmounts().linesTotal().value()).containsOnly(ars(12000));
    }

    @Test
    void enrollmentActiveOnDecemberThirtyFirstKeepsItsStateOnJanuaryFirst() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y26, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 1, 15));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, JAN_2026, date(2026, 1, 15));
        issueEntryFee(enrollment);
        runEveryMonth(JAN_2026, YearMonth.of(2026, 12), () -> assertInvariants(enrollment, y26, y27));
        today(date(2026, 12, 31));
        closeFinishedTermsOfSuspendedEnrollments();
        ProgramEnrollmentStatus statusOnLastDay = statusOf(enrollment);
        List<String> sequenceOnLastDay = sequence(enrollment);

        today(date(2027, 1, 1));
        job.run();
        closeFinishedTermsOfSuspendedEnrollments();

        assertThat(statusOnLastDay).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
        assertThat(statusOf(enrollment)).isEqualTo(statusOnLastDay);
        assertThat(sequence(enrollment)).isEqualTo(sequenceOnLastDay);
        assertInvariants(enrollment, y26, y27);
    }
}
