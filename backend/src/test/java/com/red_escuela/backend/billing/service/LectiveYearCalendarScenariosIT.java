package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-001")
@Tag("D-524")
class LectiveYearCalendarScenariosIT extends AbstractInvoiceSequenceTest {

    private Year februaryYear(Career career) {
        Year year = newYear(career, 2027, 11, 1, 1, null);
        transactionTemplate.executeWithoutResult(status ->
                entityManager.find(Term.class, year.termId()).setStartDate(LocalDateTime.of(2027, 2, 1, 0, 0)));
        return year;
    }

    private void runTheJobEveryMonthOfTheYear() {
        for (int month = 1; month <= 12; month++) {
            today(date(2027, month, 3));
            job.run();
        }
        today(date(2028, 1, 4));
        job.run();
    }

    @Test
    void aFebruaryToDecemberYearBillsElevenInstallmentsFromFebruaryAndNoneInJanuary() {
        Career career = newCareer();
        Year year = februaryYear(career);
        today(date(2027, 1, 3));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 1), date(2027, 1, 2));

        runTheJobEveryMonthOfTheYear();

        assertThat(sequence(enrollment)).isEqualTo(expected("M2027-01", "C2027-02..2027-12"));
        assertThat(periods(enrollment, FeeType.MONTHLY_INSTALLMENT)).hasSize(11).doesNotContain(YearMonth.of(2027, 1)).contains(YearMonth.of(2027, 2));
        assertInvariants(enrollment, year);
    }

    @Test
    void theDefaultMarchToDecemberYearStillBillsTenInstallmentsAndTheEnrollmentFeeInFebruary() {
        Career career = newCareer();
        Year year = newYear(career, 2027, 10, 1, 2, null);
        today(date(2027, 1, 3));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 1), date(2027, 1, 2));

        runTheJobEveryMonthOfTheYear();

        assertThat(sequence(enrollment)).isEqualTo(expected("M2027-02", "C2027-03..2027-12"));
        assertThat(periods(enrollment, FeeType.MONTHLY_INSTALLMENT)).hasSize(10).doesNotContain(YearMonth.of(2027, 1), YearMonth.of(2027, 2));
        assertInvariants(enrollment, year);
    }
}
