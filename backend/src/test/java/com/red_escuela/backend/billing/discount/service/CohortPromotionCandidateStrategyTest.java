package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.discount.domain.AppliedStudentDiscountInstallment;
import com.red_escuela.backend.billing.discount.domain.CohortPromotionAcquisition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.ScholarshipPeriodRange;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
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
import com.red_escuela.backend.enrollment.api.BillingEnrollmentLookup;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CohortPromotionCandidateStrategyTest {

    private static final EnrollmentReference ENROLLMENT = EnrollmentReference.ofProgram(10L);
    private static final StatusChangeActor ACTOR = StatusChangeActor.ofProcess("TEST");
    private static final Instant ENROLLED_AT = Instant.parse("2027-02-20T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-08-01T10:00:00Z"), ZoneOffset.UTC);
    private static final long DEFINITION_ID = 3L;
    private static final long PROMOTION_ID = 8L;

    private final EnrollmentStudentDiscountReader discountReader = mock(EnrollmentStudentDiscountReader.class);
    private final AppliedStudentDiscountRepository appliedRepository = mock(AppliedStudentDiscountRepository.class);
    private final DiscountDefinitionRepository definitionRepository = mock(DiscountDefinitionRepository.class);
    private final StudentDiscountRepository studentDiscountRepository = mock(StudentDiscountRepository.class);
    private final StudentDiscountCandidateStrategy strategy = new StudentDiscountCandidateStrategy(discountReader, mock(EnrollmentLectiveYearReader.class),
            mock(BillingEnrollmentLookup.class), mock(EnrollmentFeeIssuanceReader.class), appliedRepository, definitionRepository, studentDiscountRepository, CLOCK);

    private StudentDiscount acquire(DiscountDraftBuilder draft) {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(draft.build(), 99L, ENROLLED_AT);
        ReflectionTestUtils.setField(definition, "id", DEFINITION_ID);
        StudentDiscount promotion = StudentDiscount.acquireCohortPromotion(new CohortPromotionAcquisition(ENROLLMENT, 1L, Currency.ARS, definition,
                new ScholarshipPeriodRange(YearMonth.of(2027, 2), YearMonth.of(2027, 4)), 99L, "Promoción de cohorte"), ENROLLED_AT);
        ReflectionTestUtils.setField(promotion, "id", PROMOTION_ID);
        when(definitionRepository.findAllById(List.of(DEFINITION_ID))).thenReturn(List.of(definition));
        when(discountReader.readApplicableDiscountsAsDetachedSnapshot(ENROLLMENT)).thenReturn(List.of(promotion));
        when(discountReader.lockEnrollmentAndReadApplicableDiscounts(ENROLLMENT)).thenReturn(List.of(promotion));
        return promotion;
    }

    private DiscountDraftBuilder monthlyPromotion(Integer installments) {
        DiscountDraftBuilder draft = DiscountDraftBuilder.cohortPromotion().withPercent("30");
        return installments == null ? draft.withoutInstallments() : draft.withInstallments(installments);
    }

    private void liveUses(int count) {
        List<AppliedStudentDiscountInstallment> uses = java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(number -> new AppliedStudentDiscountInstallment(PROMOTION_ID, YearMonth.of(2027, 2 + number), number, 3)).toList();
        when(appliedRepository.findLiveApplicationsOf(List.of(PROMOTION_ID))).thenReturn(uses);
    }

    private static InvoiceIssuingContext context(FeeType feeType, LocalDate issueDate) {
        YearMonth period = YearMonth.from(issueDate);
        InvoiceIssuingRequest request = new InvoiceIssuingRequest(1L, 10L, null, 5L, null, null, feeType, period, issueDate.plusDays(10), issueDate,
                InvoiceIssuingReason.MONTHLY_RUN, null, null, feeType == FeeType.ENROLLMENT_FEE ? 1 : null, false, null, "Cargo", null, null, null, ACTOR);
        return new InvoiceIssuingContext(request, new InvoiceIssuingFacts(issueDate, Currency.ARS, NonNegativeMoney.of(new BigDecimal("80000.00"), Currency.ARS), 7L, null, null,
                new StudentSnapshot("Ana Perez", "123", null), true));
    }

    @Test
    @Tag("D-074")
    void aFreshPromotionStartsAtInstallmentOneOfThree() {
        acquire(monthlyPromotion(3));
        liveUses(0);

        List<InvoiceDiscountCandidate> candidates = strategy.findDiscountCandidatesForInvoice(context(FeeType.MONTHLY_INSTALLMENT, LocalDate.of(2027, 3, 1)));

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.installmentNumber()).isEqualTo(1);
            assertThat(candidate.installmentsTotal()).isEqualTo(3);
            assertThat(candidate.discountAmount()).isEqualTo(NonNegativeMoney.of(new BigDecimal("24000.00"), Currency.ARS));
            assertThat(candidate.discountType()).isEqualTo(DiscountType.COHORT_PROMOTION);
            assertThat(candidate.studentDiscountId()).isEqualTo(PROMOTION_ID);
            assertThat(candidate.origin()).isEqualTo(DiscountCandidateOrigin.STUDENT_DISCOUNT);
            assertThat(candidate.discountName()).isEqualTo("Promoción: Promoción de cohorte");
        });
    }

    @Test
    @Tag("D-074")
    @Tag("D-295")
    void theCounterIsTheLiveApplicationsPlusOneAndIgnoresTheMonthsLostToABetterDiscount() {
        acquire(monthlyPromotion(3));
        liveUses(1);

        List<InvoiceDiscountCandidate> candidates = strategy.findDiscountCandidatesForInvoice(context(FeeType.MONTHLY_INSTALLMENT, LocalDate.of(2027, 7, 1)));

        assertThat(candidates).singleElement().satisfies(candidate -> assertThat(candidate.installmentNumber()).isEqualTo(2));
    }

    @Test
    @Tag("D-074")
    void aPromotionWithAllItsInstallmentsUsedIsNoLongerACandidate() {
        acquire(monthlyPromotion(3));
        liveUses(3);

        assertThat(strategy.findDiscountCandidatesForInvoice(context(FeeType.MONTHLY_INSTALLMENT, LocalDate.of(2027, 8, 1)))).isEmpty();
    }

    @Test
    @Tag("D-283")
    void theInstallmentsNotUsedExpireTheDayAfterTheAvailableUntilDate() {
        acquire(monthlyPromotion(3).availableUntil(LocalDate.of(2027, 6, 30)));
        liveUses(1);

        assertThat(strategy.findDiscountCandidatesForInvoice(context(FeeType.MONTHLY_INSTALLMENT, LocalDate.of(2027, 6, 30)))).hasSize(1);
        assertThat(strategy.findDiscountCandidatesForInvoice(context(FeeType.MONTHLY_INSTALLMENT, LocalDate.of(2027, 7, 1)))).isEmpty();
        assertThat(strategy.findDiscountCandidatesUnderEnrollmentLock(context(FeeType.MONTHLY_INSTALLMENT, LocalDate.of(2027, 7, 1)))).isEmpty();
    }

    @Test
    @Tag("D-283")
    void theReferenceRangeLeftAtAcquisitionDoesNotDecideEligibility() {
        acquire(monthlyPromotion(3));
        liveUses(0);

        assertThat(strategy.findDiscountCandidatesForInvoice(context(FeeType.MONTHLY_INSTALLMENT, LocalDate.of(2028, 3, 1)))).hasSize(1);
    }

    @Test
    @Tag("D-074")
    void aPromotionWithoutInstallmentLimitKeepsCountingWithoutATotal() {
        acquire(monthlyPromotion(null));
        liveUses(3);

        List<InvoiceDiscountCandidate> candidates = strategy.findDiscountCandidatesForInvoice(context(FeeType.MONTHLY_INSTALLMENT, LocalDate.of(2027, 8, 1)));

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.installmentNumber()).isEqualTo(4);
            assertThat(candidate.installmentsTotal()).isNull();
        });
    }

    @Test
    @Tag("D-326")
    void aSingleChargePromotionIsUsedOnlyOnce() {
        acquire(monthlyPromotion(null).forInvoiceType(InvoiceType.ENROLLMENT_FEE));
        when(appliedRepository.findLiveApplicationsOf(List.of(PROMOTION_ID))).thenReturn(List.of());

        List<InvoiceDiscountCandidate> first = strategy.findDiscountCandidatesForInvoice(context(FeeType.ENROLLMENT_FEE, LocalDate.of(2027, 3, 1)));
        assertThat(first).singleElement().satisfies(candidate -> {
            assertThat(candidate.installmentNumber()).isNull();
            assertThat(candidate.installmentsTotal()).isNull();
        });

        when(appliedRepository.findLiveApplicationsOf(List.of(PROMOTION_ID))).thenReturn(List.of(new AppliedStudentDiscountInstallment(PROMOTION_ID, YearMonth.of(2027, 3), null, null)));
        assertThat(strategy.findDiscountCandidatesForInvoice(context(FeeType.ENROLLMENT_FEE, LocalDate.of(2027, 8, 1)))).isEmpty();
    }

    @Test
    @Tag("D-074")
    void aPromotionOfAnotherInvoiceTypeIsNotACandidate() {
        acquire(monthlyPromotion(3).forInvoiceType(InvoiceType.ENROLLMENT_FEE).withoutInstallments());
        liveUses(0);

        assertThat(strategy.findDiscountCandidatesForInvoice(context(FeeType.MONTHLY_INSTALLMENT, LocalDate.of(2027, 3, 1)))).isEmpty();
    }

    @Test
    @Tag("D-074")
    void anExhaustedPromotionIsNotACandidate() {
        StudentDiscount promotion = acquire(monthlyPromotion(3));
        promotion.recordLastInstallmentConsumed(ACTOR, ENROLLED_AT);
        liveUses(0);

        assertThat(strategy.findDiscountCandidatesForInvoice(context(FeeType.MONTHLY_INSTALLMENT, LocalDate.of(2027, 3, 1)))).isEmpty();
    }

    @Test
    @Tag("D-074")
    void winningTheLastInstallmentExhaustsThePromotion() {
        StudentDiscount promotion = acquire(monthlyPromotion(3));
        when(studentDiscountRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(promotion));
        InvoiceDiscountCandidate last = candidateOf(3, 3);

        strategy.markDiscountAsApplied(last);

        assertThat(promotion.getStatus()).isEqualTo(StudentDiscountStatus.EXHAUSTED);
        verify(studentDiscountRepository).save(promotion);
    }

    @Test
    @Tag("D-074")
    void winningAnInstallmentThatIsNotTheLastLeavesThePromotionActive() {
        StudentDiscount promotion = acquire(monthlyPromotion(3));
        when(studentDiscountRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(promotion));

        strategy.markDiscountAsApplied(candidateOf(2, 3));

        assertThat(promotion.getStatus()).isEqualTo(StudentDiscountStatus.ACTIVE);
        verify(studentDiscountRepository, never()).save(any());
    }

    private InvoiceDiscountCandidate candidateOf(int number, int total) {
        NonNegativeMoney base = NonNegativeMoney.of(new BigDecimal("80000.00"), Currency.ARS);
        return new InvoiceDiscountCandidate(DiscountType.COHORT_PROMOTION, PROMOTION_ID, PROMOTION_ID, "Promoción", InvoiceType.MONTHLY_INSTALLMENT, base,
                NonNegativeMoney.of(new BigDecimal("24000.00"), Currency.ARS), number, total, 1, 1, ENROLLED_AT, null, DiscountCandidateOrigin.STUDENT_DISCOUNT);
    }
}
