package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.LectiveYearSpan;
import com.red_escuela.backend.billing.discount.domain.ScholarshipPeriodRange;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrant;
import com.red_escuela.backend.billing.discount.repository.AppliedStudentDiscountRepository;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountCandidateOrigin;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingFacts;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFactsFinder;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("D-282")
@Tag("D-284")
@Tag("D-295")
@Tag("D-384")
@Tag("D-402")
@Tag("D-405")
@Tag("D-406")
class StudentDiscountCandidateStrategyTest {

    private static final EnrollmentReference ENROLLMENT = EnrollmentReference.ofProgram(10L);
    private static final StatusChangeActor ACTOR = StatusChangeActor.ofProcess("TEST");
    private static final Instant GRANTED_AT = Instant.parse("2027-02-20T10:00:00Z");
    private static final LectiveYearSpan YEAR_2027 = new LectiveYearSpan(YearMonth.of(2027, 2), YearMonth.of(2027, 12));
    private static final LectiveYearSpan YEAR_2028 = new LectiveYearSpan(YearMonth.of(2028, 2), YearMonth.of(2028, 12));

    private final EnrollmentStudentDiscountReader discountReader = mock(EnrollmentStudentDiscountReader.class);
    private final EnrollmentLectiveYearReader lectiveYearReader = mock(EnrollmentLectiveYearReader.class);
    private final BillingEnrollmentFactsFinder enrollmentLookup = mock(BillingEnrollmentFactsFinder.class);
    private final EnrollmentFeeIssuanceReader enrollmentFeeReader = mock(EnrollmentFeeIssuanceReader.class);
    private final StudentDiscountCandidateStrategy strategy = new StudentDiscountCandidateStrategy(discountReader, lectiveYearReader, enrollmentLookup, enrollmentFeeReader,
            mock(AppliedStudentDiscountRepository.class), mock(DiscountDefinitionRepository.class), mock(StudentDiscountRepository.class), Clock.systemUTC());

    private static MonetaryAdjustment percent(String points) {
        return new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal(points)));
    }

    private static NonNegativeMoney ars(String amount) {
        return NonNegativeMoney.of(new BigDecimal(amount), Currency.ARS);
    }

    private static StudentDiscount discount(long id, DiscountType type, InvoiceType invoiceType, MonetaryAdjustment calculation, NonNegativeMoney cap, int priority,
                                            YearMonth from, YearMonth to) {
        StudentDiscount discount = StudentDiscount.grantStudentDiscount(new StudentDiscountGrant(ENROLLMENT, 1L, Currency.ARS, null, type, invoiceType, calculation, cap,
                priority, new ScholarshipPeriodRange(from, to), "Situación económica"), 99L, null, GRANTED_AT);
        ReflectionTestUtils.setField(discount, "id", id);
        return discount;
    }

    private static StudentDiscount scholarship(long id, String points, YearMonth from, YearMonth to) {
        return discount(id, DiscountType.SCHOLARSHIP, InvoiceType.MONTHLY_INSTALLMENT, percent(points), null, 1, from, to);
    }

    private static StudentDiscount individual(long id, InvoiceType invoiceType, String points, YearMonth from, YearMonth to) {
        return discount(id, DiscountType.INDIVIDUAL, invoiceType, percent(points), null, 1, from, to);
    }

    private static InvoiceIssuingContext context(FeeType feeType, YearMonth period, String chargeAmount, Long programEnrollmentId, Currency currency) {
        boolean adHoc = feeType != FeeType.MONTHLY_INSTALLMENT && feeType != FeeType.ENROLLMENT_FEE;
        InvoiceIssuingRequest request = new InvoiceIssuingRequest(1L, programEnrollmentId, null, 5L, null, null, feeType, period, period.atDay(11), period.atDay(1),
                adHoc ? InvoiceIssuingReason.AD_HOC_CHARGE : InvoiceIssuingReason.MONTHLY_RUN, null, null, null, false,
                adHoc ? NonNegativeMoney.of(new BigDecimal(chargeAmount), currency) : null, "Cargo", null, null, adHoc ? "key-" + feeType : null, ACTOR);
        return new InvoiceIssuingContext(request, new InvoiceIssuingFacts(period.atDay(1), currency, NonNegativeMoney.of(new BigDecimal(chargeAmount), currency), 7L, null, null,
                new StudentSnapshot("Ana Perez", "123", null), true));
    }

    private static InvoiceIssuingContext installment(YearMonth period) {
        return context(FeeType.MONTHLY_INSTALLMENT, period, "80000.00", 10L, Currency.ARS);
    }

    private static InvoiceIssuingContext enrollmentFee(YearMonth period) {
        return context(FeeType.ENROLLMENT_FEE, period, "50000.00", 10L, Currency.ARS);
    }

    private void unlockedDiscounts(StudentDiscount... discounts) {
        when(discountReader.readApplicableDiscountsAsDetachedSnapshot(ENROLLMENT)).thenReturn(List.of(discounts));
        when(discountReader.lockEnrollmentAndReadApplicableDiscounts(ENROLLMENT)).thenReturn(List.of(discounts));
    }

    private void lectiveYearOfEnrollmentFeeIs(LectiveYearSpan span) {
        BillingEnrollmentFacts facts = new BillingEnrollmentFacts(1L, Currency.ARS, 5L, 3L, null, null);
        when(enrollmentLookup.findProgramEnrollmentFacts(10L)).thenReturn(Optional.of(facts));
        when(lectiveYearReader.findLectiveYearCovering(eq(ENROLLMENT), eq(facts), any())).thenReturn(Optional.of(span));
    }

    @Test
    void aScholarshipCoveringThePeriodIsACandidateWithItsOrdinal() {
        unlockedDiscounts(scholarship(7L, "50", YearMonth.of(2027, 3), YearMonth.of(2027, 6)));

        List<InvoiceDiscountCandidate> candidates = strategy.findDiscountCandidatesForInvoice(installment(YearMonth.of(2027, 5)));

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.installmentNumber()).isEqualTo(3);
            assertThat(candidate.installmentsTotal()).isEqualTo(4);
            assertThat(candidate.discountAmount()).isEqualTo(ars("40000.00"));
            assertThat(candidate.baseAmount()).isEqualTo(ars("80000.00"));
            assertThat(candidate.discountName()).isEqualTo("Beca: Situación económica");
        });
    }

    @Test
    void aScholarshipOutsideItsRangeIsNotACandidate() {
        unlockedDiscounts(scholarship(7L, "50", YearMonth.of(2027, 3), YearMonth.of(2027, 6)));

        assertThat(strategy.findDiscountCandidatesForInvoice(installment(YearMonth.of(2027, 2)))).isEmpty();
        assertThat(strategy.findDiscountCandidatesForInvoice(installment(YearMonth.of(2027, 7)))).isEmpty();
        assertThat(strategy.findDiscountCandidatesForInvoice(installment(YearMonth.of(2027, 3)))).hasSize(1);
        assertThat(strategy.findDiscountCandidatesForInvoice(installment(YearMonth.of(2027, 6)))).hasSize(1);
    }

    @Test
    void aRevokedScholarshipIsNotACandidateFromItsRevocationPeriod() {
        StudentDiscount revoked = scholarship(7L, "50", YearMonth.of(2027, 3), YearMonth.of(2027, 12));
        revoked.revokeStudentDiscount("Cambió la situación", YearMonth.of(2027, 6), ACTOR, GRANTED_AT.plusSeconds(60));
        unlockedDiscounts(revoked);

        assertThat(strategy.findDiscountCandidatesForInvoice(installment(YearMonth.of(2027, 5)))).hasSize(1);
        assertThat(strategy.findDiscountCandidatesForInvoice(installment(YearMonth.of(2027, 6)))).isEmpty();
        assertThat(strategy.findDiscountCandidatesForInvoice(installment(YearMonth.of(2027, 7)))).isEmpty();
    }

    @Test
    void anEndedDiscountIsNeverACandidate() {
        StudentDiscount ended = scholarship(7L, "50", YearMonth.of(2027, 3), YearMonth.of(2027, 12));
        ended.endWithEnrollment(ACTOR, GRANTED_AT.plusSeconds(60));
        unlockedDiscounts(ended);

        assertThat(strategy.findDiscountCandidatesForInvoice(installment(YearMonth.of(2027, 5)))).isEmpty();
    }

    @Test
    void anIndividualEnrollmentFeeDiscountMatchesTheEnrollmentFeeOfTheSameLectiveYear() {
        unlockedDiscounts(individual(7L, InvoiceType.ENROLLMENT_FEE, "30", YearMonth.of(2027, 4), YearMonth.of(2027, 4)));
        lectiveYearOfEnrollmentFeeIs(YEAR_2027);

        List<InvoiceDiscountCandidate> candidates = strategy.findDiscountCandidatesForInvoice(enrollmentFee(YearMonth.of(2027, 3)));

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.discountAmount()).isEqualTo(ars("15000.00"));
            assertThat(candidate.installmentNumber()).isNull();
            assertThat(candidate.installmentsTotal()).isNull();
            assertThat(candidate.discountType()).isEqualTo(DiscountType.INDIVIDUAL);
        });
    }

    @Test
    void anIndividualEnrollmentFeeDiscountIgnoresTheNextLectiveYearsEnrollmentFee() {
        unlockedDiscounts(individual(7L, InvoiceType.ENROLLMENT_FEE, "30", YearMonth.of(2027, 4), YearMonth.of(2027, 4)));
        lectiveYearOfEnrollmentFeeIs(YEAR_2028);

        assertThat(strategy.findDiscountCandidatesForInvoice(enrollmentFee(YearMonth.of(2028, 3)))).isEmpty();
    }

    @Test
    void anEnrollmentFeeWithoutALectiveYearGetsNoIndividualDiscount() {
        unlockedDiscounts(individual(7L, InvoiceType.ENROLLMENT_FEE, "30", YearMonth.of(2027, 4), YearMonth.of(2027, 4)));
        when(enrollmentLookup.findProgramEnrollmentFacts(10L)).thenReturn(Optional.empty());

        assertThat(strategy.findDiscountCandidatesForInvoice(enrollmentFee(YearMonth.of(2027, 3)))).isEmpty();
    }

    @Test
    void aRevokedEnrollmentFeeDiscountIsNotACandidate() {
        StudentDiscount revoked = individual(7L, InvoiceType.ENROLLMENT_FEE, "30", YearMonth.of(2027, 4), YearMonth.of(2027, 4));
        revoked.revokeStudentDiscount("Error de carga", YearMonth.of(2027, 8), ACTOR, GRANTED_AT.plusSeconds(60));
        unlockedDiscounts(revoked);
        lectiveYearOfEnrollmentFeeIs(YEAR_2027);

        assertThat(strategy.findDiscountCandidatesForInvoice(enrollmentFee(YearMonth.of(2027, 3)))).isEmpty();
    }

    @Test
    @Tag("D-405")
    void anEnrollmentFeeDiscountIsNotACandidateWhenALiveEnrollmentFeeOfTheLectiveYearWasIssuedBefore() {
        StudentDiscount discount = individual(7L, InvoiceType.ENROLLMENT_FEE, "30", YearMonth.of(2027, 4), YearMonth.of(2027, 4));
        unlockedDiscounts(discount);
        lectiveYearOfEnrollmentFeeIs(YEAR_2027);
        when(enrollmentFeeReader.enrollmentFeeIssuedIn(ENROLLMENT, Optional.of(YEAR_2027))).thenReturn(false, true);

        assertThat(strategy.findDiscountCandidatesForInvoice(enrollmentFee(YearMonth.of(2027, 3)))).hasSize(1);
        assertThat(strategy.findDiscountCandidatesForInvoice(enrollmentFee(YearMonth.of(2027, 8)))).isEmpty();
    }

    @Test
    @Tag("D-406")
    void anIndividualDiscountOnARetakenSubjectFeeAppliesToEveryChargeInItsRangeWithoutAConsumptionCheck() {
        unlockedDiscounts(individual(7L, InvoiceType.RETAKEN_SUBJECT_FEE, "40", YearMonth.of(2027, 3), YearMonth.of(2027, 6)));

        InvoiceIssuingContext first = context(FeeType.RETAKEN_SUBJECT_FEE, YearMonth.of(2027, 4), "30000.00", 10L, Currency.ARS);
        InvoiceIssuingContext second = context(FeeType.RETAKEN_SUBJECT_FEE, YearMonth.of(2027, 5), "30000.00", 10L, Currency.ARS);
        InvoiceIssuingContext outside = context(FeeType.RETAKEN_SUBJECT_FEE, YearMonth.of(2027, 7), "30000.00", 10L, Currency.ARS);

        assertThat(strategy.findDiscountCandidatesForInvoice(first)).singleElement().satisfies(candidate -> assertThat(candidate.discountAmount()).isEqualTo(ars("12000.00")));
        assertThat(strategy.findDiscountCandidatesForInvoice(second)).hasSize(1);
        assertThat(strategy.findDiscountCandidatesForInvoice(outside)).isEmpty();
        verifyNoInteractions(enrollmentFeeReader);
    }

    @Test
    @Tag("D-406")
    void anIndividualDiscountOnExamDegreeAndAdministrativeChargesAppliesInsideItsRange() {
        for (FeeType feeType : List.of(FeeType.EXAM_FEE, FeeType.DEGREE_ISSUANCE_FEE, FeeType.ADMINISTRATIVE_FEE)) {
            InvoiceType invoiceType = InvoiceType.valueOf(feeType.name());
            unlockedDiscounts(individual(7L, invoiceType, "10", YearMonth.of(2027, 3), YearMonth.of(2027, 6)));

            assertThat(strategy.findDiscountCandidatesForInvoice(context(feeType, YearMonth.of(2027, 5), "10000.00", 10L, Currency.ARS))).as(feeType.name()).hasSize(1);
            assertThat(strategy.findDiscountCandidatesForInvoice(context(feeType, YearMonth.of(2027, 8), "10000.00", 10L, Currency.ARS))).as(feeType.name()).isEmpty();
        }
    }

    @Test
    void aDiscountOfAnotherInvoiceTypeIsNotACandidate() {
        unlockedDiscounts(individual(7L, InvoiceType.EXAM_FEE, "50", YearMonth.of(2027, 3), YearMonth.of(2027, 6)));

        assertThat(strategy.findDiscountCandidatesForInvoice(installment(YearMonth.of(2027, 4)))).isEmpty();
    }

    @Test
    void theCandidateCarriesCalculationPriorityGrantAndStudentDiscountId() {
        MonetaryAdjustment calculation = percent("25");
        StudentDiscount discount = discount(7L, DiscountType.SCHOLARSHIP, InvoiceType.MONTHLY_INSTALLMENT, calculation, null, 3, YearMonth.of(2027, 3), YearMonth.of(2027, 6));
        unlockedDiscounts(discount);

        InvoiceDiscountCandidate candidate = strategy.findDiscountCandidatesForInvoice(installment(YearMonth.of(2027, 3))).get(0);

        assertThat(candidate.calculation()).isEqualTo(calculation);
        assertThat(candidate.priority()).isEqualTo(3);
        assertThat(candidate.grantedAt()).isEqualTo(GRANTED_AT);
        assertThat(candidate.studentDiscountId()).isEqualTo(7L);
        assertThat(candidate.sourceId()).isEqualTo(7L);
        assertThat(candidate.origin()).isEqualTo(DiscountCandidateOrigin.STUDENT_DISCOUNT);
        assertThat(candidate.lineNumber()).isEqualTo(1);
        assertThat(candidate.typeRank()).isEqualTo(1);
    }

    @Test
    void theSavingIsClippedByTheCapPerInvoice() {
        unlockedDiscounts(discount(7L, DiscountType.SCHOLARSHIP, InvoiceType.MONTHLY_INSTALLMENT, percent("50"), ars("10000.00"), 1, YearMonth.of(2027, 3), YearMonth.of(2027, 6)));

        assertThat(strategy.findDiscountCandidatesForInvoice(installment(YearMonth.of(2027, 4)))).singleElement()
                .satisfies(candidate -> assertThat(candidate.discountAmount()).isEqualTo(ars("10000.00")));
    }

    @Test
    void aFixedAmountAboveTheChargeIsClippedToTheCharge() {
        unlockedDiscounts(discount(7L, DiscountType.INDIVIDUAL, InvoiceType.MONTHLY_INSTALLMENT, new MonetaryAdjustment.Nominal(Money.of(new BigDecimal("100000.00"), Currency.ARS)),
                null, 1, YearMonth.of(2027, 3), YearMonth.of(2027, 3)));

        assertThat(strategy.findDiscountCandidatesForInvoice(installment(YearMonth.of(2027, 3)))).singleElement()
                .satisfies(candidate -> assertThat(candidate.discountAmount()).isEqualTo(ars("80000.00")));
    }

    @Test
    void aDiscountThatSavesNothingIsDiscarded() {
        unlockedDiscounts(scholarship(7L, "0", YearMonth.of(2027, 3), YearMonth.of(2027, 6)));

        assertThat(strategy.findDiscountCandidatesForInvoice(installment(YearMonth.of(2027, 4)))).isEmpty();
    }

    @Test
    void aDiscountInAnotherCurrencyIsNotACandidate() {
        unlockedDiscounts(scholarship(7L, "50", YearMonth.of(2027, 3), YearMonth.of(2027, 6)));

        assertThat(strategy.findDiscountCandidatesForInvoice(context(FeeType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 4), "100.00", 10L, Currency.USD))).isEmpty();
    }

    @Test
    void aChargeWithoutEnrollmentHasNoCandidatesAndReadsNothing() {
        InvoiceIssuingContext withoutEnrollment = context(FeeType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 4), "80000.00", null, Currency.ARS);

        assertThat(strategy.findDiscountCandidatesForInvoice(withoutEnrollment)).isEmpty();
        verifyNoInteractions(discountReader);
    }

    @Test
    void aLateFeeNeverHasCandidates() {
        InvoiceIssuingRequest request = new InvoiceIssuingRequest(1L, 10L, null, 5L, null, null, FeeType.LATE_FEE, YearMonth.of(2027, 4), LocalDate.of(2027, 4, 11),
                LocalDate.of(2027, 4, 1), InvoiceIssuingReason.LATE_FEE, null, null, null, false, null, null, null, null, null, ACTOR);
        InvoiceIssuingContext lateFee = new InvoiceIssuingContext(request, new InvoiceIssuingFacts(LocalDate.of(2027, 4, 1), Currency.ARS, ars("100.00"), 7L, null, null,
                new StudentSnapshot("Ana Perez", "123", null), true));

        assertThat(strategy.findDiscountCandidatesForInvoice(lateFee)).isEmpty();
        verifyNoInteractions(discountReader);
    }

    @Test
    void theUnlockedFinderReadsASnapshotAndTheLockedFinderTakesTheEnrollmentLock() {
        unlockedDiscounts(scholarship(7L, "50", YearMonth.of(2027, 3), YearMonth.of(2027, 6)));

        assertThat(strategy.findDiscountCandidatesForInvoice(installment(YearMonth.of(2027, 4)))).hasSize(1);
        verify(discountReader).readApplicableDiscountsAsDetachedSnapshot(ENROLLMENT);
        verify(discountReader, never()).lockEnrollmentAndReadApplicableDiscounts(any());

        assertThat(strategy.findDiscountCandidatesUnderEnrollmentLock(installment(YearMonth.of(2027, 4)))).hasSize(1);
        verify(discountReader).lockEnrollmentAndReadApplicableDiscounts(ENROLLMENT);
    }

    @Test
    void theStrategyDeclaresTheStudentDiscountOrigin() {
        assertThat(strategy.origin()).isEqualTo(DiscountCandidateOrigin.STUDENT_DISCOUNT);
    }

    @Test
    void aCourseEnrollmentChargeReadsTheDiscountsOfThatCourseEnrollment() {
        EnrollmentReference course = EnrollmentReference.ofCourse(30L);
        when(discountReader.readApplicableDiscountsAsDetachedSnapshot(course)).thenReturn(List.of(scholarship(7L, "50", YearMonth.of(2027, 3), YearMonth.of(2027, 6))));
        InvoiceIssuingRequest request = new InvoiceIssuingRequest(1L, null, 30L, null, 8L, null, FeeType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 4), LocalDate.of(2027, 4, 11),
                LocalDate.of(2027, 4, 1), InvoiceIssuingReason.COURSE_MONTHLY_RUN, null, null, null, false, null, null, null, null, null, ACTOR);
        InvoiceIssuingContext courseCharge = new InvoiceIssuingContext(request, new InvoiceIssuingFacts(LocalDate.of(2027, 4, 1), Currency.ARS, ars("80000.00"), 7L, null, null,
                new StudentSnapshot("Ana Perez", "123", null), true));

        assertThat(strategy.findDiscountCandidatesForInvoice(courseCharge)).hasSize(1);
    }

    @Test
    void theConceptCarriesTheReasonAndIsCutAtTwoHundredCharacters() {
        StudentDiscount longReason = StudentDiscount.grantStudentDiscount(new StudentDiscountGrant(ENROLLMENT, 1L, Currency.ARS, null, DiscountType.INDIVIDUAL,
                InvoiceType.MONTHLY_INSTALLMENT, percent("10"), null, 1, new ScholarshipPeriodRange(YearMonth.of(2027, 3), YearMonth.of(2027, 6)), "x".repeat(300)), 99L, null, GRANTED_AT);
        ReflectionTestUtils.setField(longReason, "id", 8L);
        unlockedDiscounts(longReason);

        assertThat(strategy.findDiscountCandidatesForInvoice(installment(YearMonth.of(2027, 4)))).singleElement()
                .satisfies(candidate -> assertThat(candidate.discountName()).hasSize(200).startsWith("Descuento individual: xxx"));
    }
}
