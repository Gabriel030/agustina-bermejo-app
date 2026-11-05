package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-074")
@Tag("D-283")
@Tag("D-286")
class CohortPromotionProgressTest {

    private static final Instant NOW = Instant.parse("2027-02-01T10:00:00Z");
    private static final LocalDate ISSUE_DATE = LocalDate.of(2027, 6, 30);

    private static StudentDiscount promotion(DiscountDraftBuilder draft) {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(draft.build(), 99L, NOW);
        ReflectionTestUtils.setField(definition, "id", 5L);
        return StudentDiscount.acquireCohortPromotion(new CohortPromotionAcquisition(EnrollmentReference.ofProgram(7L), 1L, Currency.ARS, definition,
                new ScholarshipPeriodRange(YearMonth.of(2027, 2), YearMonth.of(2027, 2)), 99L, "Promoción"), NOW);
    }

    private static DiscountDraftBuilder monthly(Integer installments) {
        DiscountDraftBuilder draft = DiscountDraftBuilder.cohortPromotion().withPercent("30");
        return installments == null ? draft.withoutInstallments() : draft.withInstallments(installments);
    }

    private static List<AppliedStudentDiscountInstallment> usedNumbers(Integer... numbers) {
        return java.util.Arrays.stream(numbers).map(number -> new AppliedStudentDiscountInstallment(1L, YearMonth.of(2027, 3), number, 3)).toList();
    }

    @Test
    void aPromotionWithoutUsesStartsAtInstallmentOneAndCanBeApplied() {
        CohortPromotionProgress progress = new CohortPromotionProgress(promotion(monthly(3)), null, List.of());

        assertThat(progress.liveUses()).isZero();
        assertThat(progress.canBeAppliedOn(ISSUE_DATE)).isTrue();
        assertThat(progress.nextInstallmentNumber()).isEqualTo(OptionalInt.of(1));
    }

    @Test
    void theNextInstallmentIsTheLowestFreeNumberSoAReleasedOneIsTakenAgain() {
        StudentDiscount promotion = promotion(monthly(3));

        assertThat(new CohortPromotionProgress(promotion, null, usedNumbers(1)).nextInstallmentNumber()).isEqualTo(OptionalInt.of(2));
        assertThat(new CohortPromotionProgress(promotion, null, usedNumbers(1, 3)).nextInstallmentNumber()).isEqualTo(OptionalInt.of(2));
        assertThat(new CohortPromotionProgress(promotion, null, usedNumbers(2, 3)).nextInstallmentNumber()).isEqualTo(OptionalInt.of(1));
        assertThat(new CohortPromotionProgress(promotion, null, usedNumbers(1, 2)).nextInstallmentNumber()).isEqualTo(OptionalInt.of(3));
    }

    @Test
    void aPromotionWithEveryInstallmentUsedCannotBeApplied() {
        CohortPromotionProgress progress = new CohortPromotionProgress(promotion(monthly(3)), null, usedNumbers(1, 2, 3));

        assertThat(progress.liveUses()).isEqualTo(3L);
        assertThat(progress.canBeAppliedOn(ISSUE_DATE)).isFalse();
    }

    @Test
    void aPromotionWithTheLastInstallmentFreeCanStillBeApplied() {
        assertThat(new CohortPromotionProgress(promotion(monthly(3)), null, usedNumbers(1, 2)).canBeAppliedOn(ISSUE_DATE)).isTrue();
    }

    @Test
    void theAvailableUntilDateIsInclusiveAndTheDayAfterExpiresTheUnusedInstallments() {
        CohortPromotionProgress progress = new CohortPromotionProgress(promotion(monthly(3)), ISSUE_DATE, usedNumbers(1));

        assertThat(progress.canBeAppliedOn(ISSUE_DATE)).isTrue();
        assertThat(progress.canBeAppliedOn(ISSUE_DATE.minusDays(1))).isTrue();
        assertThat(progress.canBeAppliedOn(ISSUE_DATE.plusDays(1))).isFalse();
    }

    @Test
    @Tag("D-426")
    void aReissueIsEvaluatedWithTheIssueDateOfTheOriginalInvoiceInsteadOfTheReissueDate() {
        LocalDate windowEnd = LocalDate.of(2027, 4, 30);
        CohortPromotionProgress progress = new CohortPromotionProgress(promotion(monthly(3)), windowEnd, usedNumbers(1));

        assertThat(progress.canBeAppliedOn(LocalDate.of(2027, 5, 10))).isFalse();
        assertThat(progress.withReissuedInvoiceOriginalIssueDate(LocalDate.of(2027, 4, 1)).canBeAppliedOn(LocalDate.of(2027, 5, 10))).isTrue();
        assertThat(progress.withReissuedInvoiceOriginalIssueDate(windowEnd).canBeAppliedOn(LocalDate.of(2027, 5, 10))).isTrue();
    }

    @Test
    @Tag("D-426")
    void anOriginalInvoiceIssuedAfterTheWindowDoesNotReopenIt() {
        CohortPromotionProgress progress = new CohortPromotionProgress(promotion(monthly(3)), LocalDate.of(2027, 4, 30), usedNumbers(1))
                .withReissuedInvoiceOriginalIssueDate(LocalDate.of(2027, 5, 1));

        assertThat(progress.canBeAppliedOn(LocalDate.of(2027, 5, 10))).isFalse();
    }

    @Test
    @Tag("D-426")
    void anOriginalDateLaterThanTheReissueDateNeverMakesTheWindowStricter() {
        CohortPromotionProgress progress = new CohortPromotionProgress(promotion(monthly(3)), LocalDate.of(2027, 4, 30), usedNumbers(1))
                .withReissuedInvoiceOriginalIssueDate(LocalDate.of(2027, 6, 1));

        assertThat(progress.canBeAppliedOn(LocalDate.of(2027, 4, 15))).isTrue();
    }

    @Test
    @Tag("D-426")
    void theOriginalDateDoesNotLiftTheLimitOfInstallments() {
        CohortPromotionProgress progress = new CohortPromotionProgress(promotion(monthly(3)), LocalDate.of(2027, 4, 30), usedNumbers(1, 2, 3))
                .withReissuedInvoiceOriginalIssueDate(LocalDate.of(2027, 4, 1));

        assertThat(progress.canBeAppliedOn(LocalDate.of(2027, 5, 10))).isFalse();
    }

    @Test
    @Tag("D-426")
    void theWindowIsClosedOnlyTheDayAfterTheAvailableUntilDateAndNeverWithoutOne() {
        LocalDate windowEnd = LocalDate.of(2027, 4, 30);
        CohortPromotionProgress bounded = new CohortPromotionProgress(promotion(monthly(3)), windowEnd, List.of());

        assertThat(bounded.isWindowClosedOn(windowEnd)).isFalse();
        assertThat(bounded.isWindowClosedOn(windowEnd.plusDays(1))).isTrue();
        assertThat(new CohortPromotionProgress(promotion(monthly(3)), null, List.of()).isWindowClosedOn(windowEnd.plusYears(9))).isFalse();
    }

    @Test
    void aMonthlyPromotionWithoutInstallmentLimitKeepsNumberingPastAnyCount() {
        Integer[] numbers = IntStream.rangeClosed(1, 40).boxed().toArray(Integer[]::new);
        CohortPromotionProgress progress = new CohortPromotionProgress(promotion(monthly(null)), null, usedNumbers(numbers));

        assertThat(progress.canBeAppliedOn(ISSUE_DATE)).isTrue();
        assertThat(progress.nextInstallmentNumber()).isEqualTo(OptionalInt.of(41));
    }

    @Test
    void aPromotionOnANonMonthlyChargeIsSingleUseAndHasNoInstallmentNumber() {
        StudentDiscount promotion = promotion(monthly(null).forInvoiceType(InvoiceType.ENROLLMENT_FEE));
        assertThat(promotion.getApplicableInvoiceType()).isEqualTo(InvoiceType.ENROLLMENT_FEE);

        CohortPromotionProgress unused = new CohortPromotionProgress(promotion, null, List.of());
        CohortPromotionProgress used = new CohortPromotionProgress(promotion, null, usedNumbers((Integer) null));

        assertThat(unused.canBeAppliedOn(ISSUE_DATE)).isTrue();
        assertThat(unused.nextInstallmentNumber()).isEmpty();
        assertThat(used.liveUses()).isEqualTo(1L);
        assertThat(used.canBeAppliedOn(ISSUE_DATE)).isFalse();
    }

    @Test
    void theLiveApplicationsAreCopiedDefensively() {
        java.util.ArrayList<AppliedStudentDiscountInstallment> mutable = new java.util.ArrayList<>(usedNumbers(1));
        CohortPromotionProgress progress = new CohortPromotionProgress(promotion(monthly(3)), null, mutable);

        mutable.clear();

        assertThat(progress.liveUses()).isEqualTo(1L);
    }

    @Test
    void anExhaustedPromotionReturnsToActiveWhenItsConsumptionIsReturnedAndRecordsTheChange() {
        StudentDiscount promotion = promotion(monthly(3));
        com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor actor = com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor.ofProcess("TEST");
        promotion.recordLastInstallmentConsumed(actor, NOW);
        assertThat(promotion.getStatus()).isEqualTo(StudentDiscountStatus.EXHAUSTED);
        int changesBefore = promotion.getStatusChanges().size();

        promotion.returnInstallmentConsumption(actor, NOW);

        assertThat(promotion.getStatus()).isEqualTo(StudentDiscountStatus.ACTIVE);
        assertThat(promotion.getStatusChanges()).hasSize(changesBefore + 1);
    }

    @Test
    void aProgressWithoutPromotionIsRejected() {
        assertThatThrownBy(() -> new CohortPromotionProgress(null, null, List.of())).isInstanceOf(NullPointerException.class).hasMessageContaining("promotion");
    }
}
