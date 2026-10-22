package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountContest;
import com.red_escuela.backend.billing.invoice.policy.issuing.BestDiscountPerInvoiceLinePolicy;
import com.red_escuela.backend.billing.invoice.policy.issuing.DiscountMatchesInvoiceTypePolicy;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.candidate;
import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.candidateOnLine;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-073")
@Tag("D-129")
class InvoiceDiscountCalculatorTest {

    private final InvoiceDiscountCalculator calculator = new InvoiceDiscountCalculator(
            new InvoiceIssuingOperationConfiguration().invoiceDiscountSelectionPolicySet());

    private static final InvoiceType MONTHLY = InvoiceType.MONTHLY_INSTALLMENT;

    @Test
    void scholarshipHalfBeatsPromotionThirtyPercent() {
        var scholarship = candidate(DiscountType.SCHOLARSHIP, 1, MONTHLY, "80000.00", "40000.00");
        var promotion = candidate(DiscountType.COHORT_PROMOTION, 2, MONTHLY, "80000.00", "24000.00");

        assertThat(calculator.selectBestDiscountPerInvoiceLine(MONTHLY, List.of(promotion, scholarship))).containsExactly(Map.entry(1, scholarship));
    }

    @Test
    void thirtyPercentPromotionBeatsTwentyPercentPromotion() {
        var thirty = candidate(DiscountType.COHORT_PROMOTION, 9, MONTHLY, "100.00", "30.00");
        var twenty = candidate(DiscountType.COHORT_PROMOTION, 1, MONTHLY, "100.00", "20.00");

        assertThat(calculator.selectBestDiscountPerInvoiceLine(MONTHLY, List.of(twenty, thirty))).containsExactly(Map.entry(1, thirty));
    }

    @Test
    @Tag("D-295")
    void aTieOfSavingGoesToTheScholarshipWhateverTheInputOrder() {
        var promotion = candidate(DiscountType.COHORT_PROMOTION, 1, MONTHLY, "100.00", "30.00");
        var scholarship = candidate(DiscountType.SCHOLARSHIP, 8, MONTHLY, "100.00", "30.00");

        assertThat(calculator.selectBestDiscountPerInvoiceLine(MONTHLY, List.of(promotion, scholarship))).containsExactly(Map.entry(1, scholarship));
        assertThat(calculator.selectBestDiscountPerInvoiceLine(MONTHLY, List.of(scholarship, promotion))).containsExactly(Map.entry(1, scholarship));
    }

    @Test
    void aTieOfSavingAndTypeGoesToTheLowestSourceIdWhateverTheInputOrder() {
        var low = candidate(DiscountType.COHORT_PROMOTION, 3, MONTHLY, "100.00", "30.00");
        var high = candidate(DiscountType.COHORT_PROMOTION, 8, MONTHLY, "100.00", "30.00");

        assertThat(calculator.selectBestDiscountPerInvoiceLine(MONTHLY, List.of(high, low))).containsExactly(Map.entry(1, low));
        assertThat(calculator.selectBestDiscountPerInvoiceLine(MONTHLY, List.of(low, high))).containsExactly(Map.entry(1, low));
    }

    @Test
    void twoCandidatesThatRankEqualStillGiveASingleWinnerTheFirstOneListed() {
        var first = candidate(DiscountType.COHORT_PROMOTION, 4, MONTHLY, "100.00", "30.00");
        var second = candidate(DiscountType.COHORT_PROMOTION, 4, MONTHLY, "100.00", "30.00");

        assertThat(calculator.selectBestDiscountPerInvoiceLine(MONTHLY, List.of(first, second)).get(1)).isSameAs(first);
        assertThat(calculator.selectBestDiscountPerInvoiceLine(MONTHLY, List.of(second, first)).get(1)).isSameAs(second);
    }

    @Test
    @Tag("D-301")
    void eachLineGetsItsOwnBestDiscount() {
        var scholarshipOnFirstLine = candidateOnLine(1, DiscountType.SCHOLARSHIP, 1, MONTHLY, "80000.00", "40000.00");
        var promotionOnFirstLine = candidateOnLine(1, DiscountType.COHORT_PROMOTION, 2, MONTHLY, "80000.00", "24000.00");
        var smallPromotionOnSecondLine = candidateOnLine(2, DiscountType.COHORT_PROMOTION, 3, MONTHLY, "50000.00", "5000.00");
        var bigPromotionOnSecondLine = candidateOnLine(2, DiscountType.COHORT_PROMOTION, 4, MONTHLY, "50000.00", "15000.00");

        var winners = calculator.selectBestDiscountPerInvoiceLine(MONTHLY,
                List.of(promotionOnFirstLine, smallPromotionOnSecondLine, scholarshipOnFirstLine, bigPromotionOnSecondLine));

        assertThat(winners).containsOnly(Map.entry(1, scholarshipOnFirstLine), Map.entry(2, bigPromotionOnSecondLine));
    }

    @Test
    @Tag("D-301")
    void aCandidateOfAnotherLineNeverCompetes() {
        var smallOnFirstLine = candidateOnLine(1, DiscountType.COHORT_PROMOTION, 1, MONTHLY, "100.00", "10.00");
        var hugeOnSecondLine = candidateOnLine(2, DiscountType.SCHOLARSHIP, 2, MONTHLY, "100000.00", "90000.00");

        var winners = calculator.selectBestDiscountPerInvoiceLine(MONTHLY, List.of(hugeOnSecondLine, smallOnFirstLine));

        assertThat(winners).containsOnly(Map.entry(1, smallOnFirstLine), Map.entry(2, hugeOnSecondLine));
    }

    @Test
    @Tag("D-129")
    void aCandidateOfAnotherInvoiceTypeIsNeverSelected() {
        var enrollment = candidate(DiscountType.COHORT_ENTRY, 1, InvoiceType.ENROLLMENT_FEE, "100.00", "90.00");
        var installment = candidate(DiscountType.COHORT_PROMOTION, 2, MONTHLY, "100.00", "10.00");
        var smallerInstallment = candidate(DiscountType.SCHOLARSHIP, 3, MONTHLY, "100.00", "5.00");

        assertThat(calculator.selectBestDiscountPerInvoiceLine(MONTHLY, List.of(enrollment, installment, smallerInstallment))).containsExactly(Map.entry(1, installment));
        assertThat(calculator.selectBestDiscountPerInvoiceLine(InvoiceType.ENROLLMENT_FEE, List.of(enrollment, installment, smallerInstallment))).containsExactly(Map.entry(1, enrollment));
        assertThat(calculator.selectBestDiscountPerInvoiceLine(InvoiceType.EXAM_FEE, List.of(enrollment, installment))).isEmpty();
    }

    @Test
    void aLineWithoutCandidatesGetsNoDiscount() {
        var onlyOnSecondLine = candidateOnLine(2, DiscountType.COHORT_PROMOTION, 1, MONTHLY, "100.00", "10.00");

        assertThat(calculator.selectBestDiscountPerInvoiceLine(MONTHLY, List.of(onlyOnSecondLine))).containsOnlyKeys(2);
        assertThat(calculator.selectBestDiscountPerInvoiceLine(MONTHLY, List.of())).isEmpty();
    }

    @Test
    void theSelectionMomentListsTypeThenBestLineRule() {
        PolicySet<DiscountContest> set = new InvoiceIssuingOperationConfiguration().invoiceDiscountSelectionPolicySet();

        assertThat(set.moment()).isEqualTo("INVOICE_DISCOUNT_SELECTION");
        assertThat(set.policies().stream().map(Policy::ruleName).toList()).containsExactly(
                new DiscountMatchesInvoiceTypePolicy().ruleName(), new BestDiscountPerInvoiceLinePolicy().ruleName());
    }

    @Test
    @Tag("D-051")
    void theIssuingMomentHasTheSevenPoliciesInOrder() {
        var set = new InvoiceIssuingOperationConfiguration().invoiceIssuingPolicySet();

        assertThat(set.moment()).isEqualTo("INVOICE_ISSUING");
        assertThat(set.policies().stream().map(Policy::ruleName).toList()).containsExactly("InvoiceIssuingReasonMatchesFeeTypePolicy",
                "DueDateNotBeforeIssueDatePolicy", "EnrollmentFeeSlotRequiredPolicy", "ChargeRecipientMatchesEnrollmentPolicy", "OfferingCurrencyPolicy", "ChargeKeyNotAlreadyUsedPolicy",
                "EnrollmentFeeSlotWithinTemplateCountPolicy");
    }
}
