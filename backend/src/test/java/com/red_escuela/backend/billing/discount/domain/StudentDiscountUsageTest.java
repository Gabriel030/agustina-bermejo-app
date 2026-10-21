package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-282")
class StudentDiscountUsageTest {
    private static final Instant NOW = Instant.parse("2027-03-10T10:00:00Z");
    private static final YearMonth MARCH = YearMonth.of(2027, 3);
    private static final YearMonth APRIL = YearMonth.of(2027, 4);
    private static final YearMonth MAY = YearMonth.of(2027, 5);
    private static final YearMonth JUNE = YearMonth.of(2027, 6);
    private static final YearMonth JULY = YearMonth.of(2027, 7);
    private static final YearMonth AUGUST = YearMonth.of(2027, 8);
    private static final YearMonth DECEMBER = YearMonth.of(2027, 12);

    private StudentDiscount discount(InvoiceType invoiceType, YearMonth from, YearMonth to) {
        return StudentDiscount.grantStudentDiscount(new StudentDiscountGrant(EnrollmentReference.ofProgram(1L), 7L, Currency.ARS, null, DiscountType.SCHOLARSHIP, invoiceType,
                new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal("50"))), null, 1, new ScholarshipPeriodRange(from, to), "motivo"), 3L, null, NOW);
    }

    private AppliedStudentDiscountInstallment applied(YearMonth period, int number, int total) {
        return new AppliedStudentDiscountInstallment(1L, period, number, total);
    }

    @Test
    void anIssuedMonthOfTheRangeWithoutThisDiscountIsALostMonth() {
        StudentDiscount scholarship = discount(InvoiceType.MONTHLY_INSTALLMENT, MARCH, JUNE);

        StudentDiscountUsage usage = StudentDiscountUsage.of(scholarship, List.of(applied(MARCH, 1, 4), applied(MAY, 3, 4), applied(JUNE, 4, 4)),
                Set.of(MARCH, APRIL, MAY, JUNE, JULY));

        assertThat(usage.lostPeriods()).containsExactly(APRIL);
        assertThat(usage.appliedInstallments()).extracting(AppliedStudentDiscountInstallment::installmentNumber).containsExactly(1, 3, 4);
    }

    @Test
    void aMonthNotIssuedYetIsNotLost() {
        StudentDiscount scholarship = discount(InvoiceType.MONTHLY_INSTALLMENT, MARCH, DECEMBER);

        StudentDiscountUsage usage = StudentDiscountUsage.of(scholarship, List.of(applied(MARCH, 1, 10)), Set.of(MARCH, APRIL));

        assertThat(usage.lostPeriods()).containsExactly(APRIL);
    }

    @Test
    void theMonthsFromTheRevocationOnAreNotLost() {
        StudentDiscount scholarship = discount(InvoiceType.MONTHLY_INSTALLMENT, MARCH, DECEMBER);
        scholarship.revokeStudentDiscount("motivo", JUNE, StatusChangeActor.ofPerson(3L), NOW);

        StudentDiscountUsage usage = StudentDiscountUsage.of(scholarship, List.of(applied(MARCH, 1, 10), applied(APRIL, 2, 10)), Set.of(MARCH, APRIL, MAY, JUNE, JULY, AUGUST));

        assertThat(usage.lostPeriods()).containsExactly(MAY);
    }

    @Test
    void anEndedDiscountKeepsReportingTheMonthsItLostBeforeTheEnd() {
        StudentDiscount scholarship = discount(InvoiceType.MONTHLY_INSTALLMENT, MARCH, DECEMBER);
        scholarship.endWithEnrollment(StatusChangeActor.ofProcess("ENROLLMENT_ENDED"), NOW);

        StudentDiscountUsage usage = StudentDiscountUsage.of(scholarship, List.of(applied(MARCH, 1, 10)), Set.of(MARCH, APRIL));

        assertThat(usage.lostPeriods()).containsExactly(APRIL);
    }

    @Test
    void aDiscountOnAnotherKindOfChargeNeverReportsLostMonths() {
        StudentDiscount feeDiscount = discount(InvoiceType.ENROLLMENT_FEE, MARCH, MARCH);

        StudentDiscountUsage usage = StudentDiscountUsage.of(feeDiscount, List.of(), Set.of(MARCH, APRIL));

        assertThat(usage.lostPeriods()).isEmpty();
    }

    @Test
    void theLostMonthsComeSortedWhateverTheOrderOfTheIssuedOnes() {
        StudentDiscount scholarship = discount(InvoiceType.MONTHLY_INSTALLMENT, MARCH, DECEMBER);

        StudentDiscountUsage usage = StudentDiscountUsage.of(scholarship, List.of(), Set.of(JUNE, MARCH, MAY));

        assertThat(usage.lostPeriods()).containsExactly(MARCH, MAY, JUNE);
    }
}
