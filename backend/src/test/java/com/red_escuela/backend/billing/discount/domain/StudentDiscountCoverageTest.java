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

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-282")
@Tag("D-284")
class StudentDiscountCoverageTest {
    private static final Instant NOW = Instant.parse("2027-03-10T10:00:00Z");
    private static final YearMonth MARCH = YearMonth.of(2027, 3);
    private static final YearMonth MAY = YearMonth.of(2027, 5);
    private static final YearMonth JUNE = YearMonth.of(2027, 6);

    private StudentDiscount scholarship() {
        return StudentDiscount.grantStudentDiscount(new StudentDiscountGrant(EnrollmentReference.ofProgram(1L), 7L, Currency.ARS, null, DiscountType.SCHOLARSHIP,
                InvoiceType.MONTHLY_INSTALLMENT, new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal("50"))), null, 1,
                new ScholarshipPeriodRange(MARCH, JUNE), "motivo"), 3L, null, NOW);
    }

    @Test
    void theRangeCoversItsBordersAndNothingOutside() {
        StudentDiscount discount = scholarship();

        assertThat(discount.rangeCoversPeriod(MARCH)).isTrue();
        assertThat(discount.rangeCoversPeriod(JUNE)).isTrue();
        assertThat(discount.rangeCoversPeriod(MARCH.minusMonths(1))).isFalse();
        assertThat(discount.rangeCoversPeriod(JUNE.plusMonths(1))).isFalse();
        assertThat(discount.coversPeriod(MAY)).isTrue();
    }

    @Test
    void aRevocationCutsTheRangeFromItsFirstAffectedPeriod() {
        StudentDiscount discount = scholarship();
        discount.revokeStudentDiscount("motivo", MAY, StatusChangeActor.ofPerson(3L), NOW);

        assertThat(discount.rangeCoversPeriod(MAY.minusMonths(1))).isTrue();
        assertThat(discount.rangeCoversPeriod(MAY)).isFalse();
        assertThat(discount.coversPeriod(MAY.minusMonths(1))).isTrue();
        assertThat(discount.coversPeriod(MAY)).isFalse();
    }

    @Test
    void anEndedDiscountCoversNoPeriodButItsRangeIsStillKnown() {
        StudentDiscount discount = scholarship();
        discount.endWithEnrollment(StatusChangeActor.ofProcess("ENROLLMENT_ENDED"), NOW);

        assertThat(discount.coversPeriod(MAY)).isFalse();
        assertThat(discount.rangeCoversPeriod(MAY)).isTrue();
    }
}
