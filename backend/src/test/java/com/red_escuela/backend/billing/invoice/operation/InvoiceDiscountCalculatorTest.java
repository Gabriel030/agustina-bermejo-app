package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountContest;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.policy.issuing.BestDiscountPerInvoiceTypePolicy;
import com.red_escuela.backend.billing.invoice.policy.issuing.DiscountMatchesInvoiceTypePolicy;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.candidate;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-073")
@Tag("D-129")
class InvoiceDiscountCalculatorTest {

    private final InvoiceDiscountCalculator calculator = new InvoiceDiscountCalculator(
            new InvoiceIssuingOperationConfiguration().invoiceDiscountSelectionPolicySet());

    @Test
    void scholarshipHalfBeatsPromotionThirtyPercent() {
        var scholarship = candidate(DiscountType.SCHOLARSHIP, 1, InvoiceType.MONTHLY_INSTALLMENT, "80000.00", "40000.00");
        var promotion = candidate(DiscountType.PROMOTION, 2, InvoiceType.MONTHLY_INSTALLMENT, "80000.00", "24000.00");

        assertThat(calculator.selectBestDiscountForInvoice(InvoiceType.MONTHLY_INSTALLMENT, List.of(promotion, scholarship))).contains(scholarship);
    }

    @Test
    void thirtyPercentPromotionBeatsTwentyPercentPromotion() {
        var thirty = candidate(DiscountType.PROMOTION, 9, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "30.00");
        var twenty = candidate(DiscountType.PROMOTION, 1, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "20.00");

        assertThat(calculator.selectBestDiscountForInvoice(InvoiceType.MONTHLY_INSTALLMENT, List.of(twenty, thirty))).contains(thirty);
    }

    @Test
    void aTieGoesToTheLowestSourceIdWhateverTheInputOrder() {
        var low = candidate(DiscountType.PROMOTION, 3, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "30.00");
        var high = candidate(DiscountType.PROMOTION, 8, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "30.00");

        assertThat(calculator.selectBestDiscountForInvoice(InvoiceType.MONTHLY_INSTALLMENT, List.of(high, low))).contains(low);
        assertThat(calculator.selectBestDiscountForInvoice(InvoiceType.MONTHLY_INSTALLMENT, List.of(low, high))).contains(low);
    }

    @Test
    void aCandidateOfAnotherInvoiceTypeIsNeverSelectedEvenIfItIsBigger() {
        var enrollment = candidate(DiscountType.COHORT_ENTRY, 1, InvoiceType.ENROLLMENT_FEE, "100.00", "90.00");
        var installment = candidate(DiscountType.PROMOTION, 2, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "10.00");
        var smallerInstallment = candidate(DiscountType.SCHOLARSHIP, 3, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "5.00");

        assertThat(calculator.selectBestDiscountForInvoice(InvoiceType.MONTHLY_INSTALLMENT, List.of(enrollment, installment, smallerInstallment))).contains(installment);
        assertThat(calculator.selectBestDiscountForInvoice(InvoiceType.ENROLLMENT_FEE, List.of(enrollment, installment, smallerInstallment))).contains(enrollment);
        assertThat(calculator.selectBestDiscountForInvoice(InvoiceType.EXAM_FEE, List.of(enrollment, installment))).isEmpty();
    }

    @Test
    void withoutCandidatesThereIsNoWinner() {
        assertThat(calculator.selectBestDiscountForInvoice(InvoiceType.MONTHLY_INSTALLMENT, List.of())).isEqualTo(Optional.<InvoiceDiscountCandidate>empty());
    }

    @Test
    void theSelectionMomentListsTypeThenBestRule() {
        PolicySet<DiscountContest> set = new InvoiceIssuingOperationConfiguration().invoiceDiscountSelectionPolicySet();

        assertThat(set.moment()).isEqualTo("INVOICE_DISCOUNT_SELECTION");
        assertThat(set.policies().stream().map(Policy::ruleName).toList()).containsExactly(
                new DiscountMatchesInvoiceTypePolicy().ruleName(), new BestDiscountPerInvoiceTypePolicy().ruleName());
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
