package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.discount.exception.EnrollmentFeeAlreadyIssuedException;
import com.red_escuela.backend.billing.discount.exception.StudentDiscountOverlapException;
import com.red_escuela.backend.billing.discount.policy.StudentDiscountRejections;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-401")
@Tag("D-402")
class StudentDiscountOverlapTest {
    private static final Instant NOW = Instant.parse("2027-03-10T10:00:00Z");

    private StudentDiscount discount(EnrollmentReference enrollment, DiscountType type, InvoiceType invoiceType, YearMonth from, YearMonth to) {
        return StudentDiscount.grantStudentDiscount(new StudentDiscountGrant(enrollment, 7L, Currency.ARS, null, type, invoiceType,
                new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal("50"))), null, 1, new ScholarshipPeriodRange(from, to), "motivo"), 3L, null, NOW);
    }

    private StudentDiscount monthlyIndividual(YearMonth from, YearMonth to) {
        return discount(EnrollmentReference.ofProgram(1L), DiscountType.INDIVIDUAL, InvoiceType.MONTHLY_INSTALLMENT, from, to);
    }

    @Test
    void aRangeOverlapsWhenItSharesAtLeastOneMonthIncludingTheBoundaries() {
        StudentDiscount existing = monthlyIndividual(YearMonth.of(2027, 5), YearMonth.of(2027, 8));
        DiscountType type = DiscountType.INDIVIDUAL;
        InvoiceType invoice = InvoiceType.MONTHLY_INSTALLMENT;

        assertThat(existing.overlapsRangeOfSameKind(type, invoice, YearMonth.of(2027, 3), YearMonth.of(2027, 5))).isTrue();
        assertThat(existing.overlapsRangeOfSameKind(type, invoice, YearMonth.of(2027, 8), YearMonth.of(2027, 10))).isTrue();
        assertThat(existing.overlapsRangeOfSameKind(type, invoice, YearMonth.of(2027, 6), YearMonth.of(2027, 7))).isTrue();
        assertThat(existing.overlapsRangeOfSameKind(type, invoice, YearMonth.of(2027, 1), YearMonth.of(2027, 12))).isTrue();
        assertThat(existing.overlapsRangeOfSameKind(type, invoice, YearMonth.of(2027, 3), YearMonth.of(2027, 4))).isFalse();
        assertThat(existing.overlapsRangeOfSameKind(type, invoice, YearMonth.of(2027, 9), YearMonth.of(2027, 10))).isFalse();
    }

    @Test
    void aRangeOnlyOverlapsAnActiveDiscountOfTheSameTypeAndInvoiceType() {
        StudentDiscount existing = monthlyIndividual(YearMonth.of(2027, 5), YearMonth.of(2027, 8));
        YearMonth from = YearMonth.of(2027, 5);
        YearMonth to = YearMonth.of(2027, 8);

        assertThat(existing.overlapsRangeOfSameKind(DiscountType.SCHOLARSHIP, InvoiceType.MONTHLY_INSTALLMENT, from, to)).isFalse();
        assertThat(existing.overlapsRangeOfSameKind(DiscountType.INDIVIDUAL, InvoiceType.ENROLLMENT_FEE, from, to)).isFalse();
        existing.revokeStudentDiscount("motivo", YearMonth.of(2027, 6), StatusChangeActor.ofPerson(3L), NOW);
        assertThat(existing.overlapsRangeOfSameKind(DiscountType.INDIVIDUAL, InvoiceType.MONTHLY_INSTALLMENT, from, to)).isFalse();
    }

    @Test
    void anEnrollmentFeeDiscountCoversTheLectiveYearThatContainsItsMonth() {
        StudentDiscount existing = discount(EnrollmentReference.ofProgram(1L), DiscountType.INDIVIDUAL, InvoiceType.ENROLLMENT_FEE, YearMonth.of(2027, 4), YearMonth.of(2027, 4));
        LectiveYearSpan year = new LectiveYearSpan(YearMonth.of(2027, 2), YearMonth.of(2027, 12));

        assertThat(existing.coversLectiveYearOfSameKind(DiscountType.INDIVIDUAL, InvoiceType.ENROLLMENT_FEE, year)).isTrue();
        assertThat(existing.coversLectiveYearOfSameKind(DiscountType.INDIVIDUAL, InvoiceType.ENROLLMENT_FEE, new LectiveYearSpan(YearMonth.of(2028, 2), YearMonth.of(2028, 12)))).isFalse();
        assertThat(existing.coversLectiveYearOfSameKind(DiscountType.SCHOLARSHIP, InvoiceType.ENROLLMENT_FEE, year)).isFalse();
        assertThat(existing.coversLectiveYearOfSameKind(DiscountType.INDIVIDUAL, InvoiceType.MONTHLY_INSTALLMENT, year)).isFalse();
        existing.revokeStudentDiscount("motivo", YearMonth.of(2027, 6), StatusChangeActor.ofPerson(3L), NOW);
        assertThat(existing.coversLectiveYearOfSameKind(DiscountType.INDIVIDUAL, InvoiceType.ENROLLMENT_FEE, year)).isFalse();
    }

    @Test
    void aLectiveYearContainsItsFirstAndLastMonthButNothingOutside() {
        LectiveYearSpan year = new LectiveYearSpan(YearMonth.of(2027, 2), YearMonth.of(2027, 12));

        assertThat(year.contains(YearMonth.of(2027, 2))).isTrue();
        assertThat(year.contains(YearMonth.of(2027, 12))).isTrue();
        assertThat(year.contains(YearMonth.of(2027, 1))).isFalse();
        assertThat(year.contains(YearMonth.of(2028, 1))).isFalse();
    }

    @Test
    void aGrantKeepsTheEnrollmentKindInTheMatchingColumnOnly() {
        StudentDiscount onProgram = discount(EnrollmentReference.ofProgram(5L), DiscountType.INDIVIDUAL, InvoiceType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 4), YearMonth.of(2027, 5));
        StudentDiscount onCourse = discount(EnrollmentReference.ofCourse(6L), DiscountType.INDIVIDUAL, InvoiceType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 4), YearMonth.of(2027, 5));

        assertThat(onProgram.getProgramEnrollmentId()).isEqualTo(5L);
        assertThat(onProgram.getCourseEnrollmentId()).isNull();
        assertThat(onCourse.getCourseEnrollmentId()).isEqualTo(6L);
        assertThat(onCourse.getProgramEnrollmentId()).isNull();
        assertThat(EnrollmentReference.ofCourse(6L).kind()).isEqualTo(EnrollmentKind.COURSE);
    }

    @Test
    void aRequestWithAResolvedEndKeepsEveryOtherField() {
        MonetaryAdjustment calculation = new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal("25")));
        StudentDiscountGrantRequest request = new StudentDiscountGrantRequest(DiscountType.SCHOLARSHIP, 9L, calculation, InvoiceType.MONTHLY_INSTALLMENT,
                YearMonth.of(2027, 4), null, "motivo");

        StudentDiscountGrantRequest resolved = request.withToPeriod(YearMonth.of(2027, 12));

        assertThat(resolved).isEqualTo(new StudentDiscountGrantRequest(DiscountType.SCHOLARSHIP, 9L, calculation, InvoiceType.MONTHLY_INSTALLMENT,
                YearMonth.of(2027, 4), YearMonth.of(2027, 12), "motivo"));
    }

    @Test
    void theOverlapAndEnrollmentFeeCodesMapToTheirConflictExceptions() {
        assertThat(StudentDiscountRejections.rejectionOf(List.of(new PolicyViolation("STUDENT_DISCOUNT_OVERLAP", "x")))).isInstanceOf(StudentDiscountOverlapException.class);
        assertThat(StudentDiscountRejections.rejectionOf(List.of(new PolicyViolation("ENROLLMENT_FEE_ALREADY_ISSUED", "x")))).isInstanceOf(EnrollmentFeeAlreadyIssuedException.class);
    }
}
