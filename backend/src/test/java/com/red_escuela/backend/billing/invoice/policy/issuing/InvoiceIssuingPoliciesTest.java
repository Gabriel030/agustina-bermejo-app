package com.red_escuela.backend.billing.invoice.policy.issuing;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountContest;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.*;
import static org.assertj.core.api.Assertions.assertThat;

class InvoiceIssuingPoliciesTest {

    @ParameterizedTest
    @Tag("D-050")
    @CsvSource({
            "MONTHLY_INSTALLMENT,MONTHLY_RUN,true", "ENROLLMENT_FEE,MONTHLY_RUN,true", "EXAM_FEE,MONTHLY_RUN,false",
            "MONTHLY_INSTALLMENT,COURSE_MONTHLY_RUN,true", "ENROLLMENT_FEE,PAYMENT_PLAN_SELECTION,true", "MONTHLY_INSTALLMENT,REACTIVATION,true",
            "ENROLLMENT_FEE,ENROLLMENT_FEE_ON_DEMAND,true", "MONTHLY_INSTALLMENT,YEAR_CONFIGURATION_CHANGE,true",
            "EXAM_FEE,AD_HOC_CHARGE,true", "RETAKEN_SUBJECT_FEE,AD_HOC_CHARGE,true", "DEGREE_ISSUANCE_FEE,AD_HOC_CHARGE,true",
            "ADMINISTRATIVE_FEE,AD_HOC_CHARGE,true", "MONTHLY_INSTALLMENT,AD_HOC_CHARGE,false", "ENROLLMENT_FEE,AD_HOC_CHARGE,false",
            "ENROLLMENT_FEE,PROGRAM_ADMISSION,true", "MONTHLY_INSTALLMENT,PROGRAM_ADMISSION,false",
            "MONTHLY_INSTALLMENT,COURSE_ADMISSION,true", "ENROLLMENT_FEE,COURSE_ADMISSION,false",
            "MONTHLY_INSTALLMENT,LEGACY,false", "MONTHLY_INSTALLMENT,UPFRONT,false", "MONTHLY_INSTALLMENT,CATCH_UP,false",
            "MONTHLY_INSTALLMENT,ANNUAL_UPFRONT_PAYMENT,false", "LATE_FEE,LATE_FEE,false"})
    void reasonAndFeeTypeMustMatch(FeeType feeType, InvoiceIssuingReason reason, boolean allowed) {
        InvoiceIssuingRequest request = request(feeType, reason, TODAY, 1L, 1);
        PolicyVerdict verdict = new InvoiceIssuingReasonMatchesFeeTypePolicy().evaluateRule(context(request, Currency.ARS, null, null));

        assertThat(verdict.allowed()).isEqualTo(allowed);
        assertThat(allowed ? null : verdict.code()).isEqualTo(allowed ? null : "FEE_TYPE_NOT_ALLOWED_FOR_REASON");
    }

    @Test
    @Tag("D-057")
    void dueDateEqualToTheIssueDateIsAllowedAndYesterdayIsRejected() {
        DueDateNotBeforeIssueDatePolicy policy = new DueDateNotBeforeIssueDatePolicy();

        assertThat(policy.evaluateRule(context(request(FeeType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, TODAY, null, null), Currency.ARS, null, null)).allowed()).isTrue();
        PolicyVerdict denial = policy.evaluateRule(context(request(FeeType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, TODAY.minusDays(1), null, null), Currency.ARS, null, null));
        assertThat(denial.allowed()).isFalse();
        assertThat(denial.code()).isEqualTo("DUE_DATE_BEFORE_ISSUE_DATE");
        assertThat(policy.ruleName()).isEqualTo("DueDateNotBeforeIssueDatePolicy");
    }

    @Test
    @Tag("D-009")
    void aProgramEnrollmentFeeNeedsTemplateAndSlot() {
        EnrollmentFeeSlotRequiredPolicy policy = new EnrollmentFeeSlotRequiredPolicy();
        InvoiceIssuingContext withoutSlot = context(request(FeeType.ENROLLMENT_FEE, InvoiceIssuingReason.MONTHLY_RUN, TODAY, 5L, null), Currency.ARS, null, null);
        InvoiceIssuingContext withoutTemplate = context(request(FeeType.ENROLLMENT_FEE, InvoiceIssuingReason.MONTHLY_RUN, TODAY, null, 1), Currency.ARS, null, null);
        InvoiceIssuingContext complete = context(request(FeeType.ENROLLMENT_FEE, InvoiceIssuingReason.MONTHLY_RUN, TODAY, 5L, 1), Currency.ARS, null, null);
        InvoiceIssuingContext installment = context(request(FeeType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, TODAY, null, null), Currency.ARS, null, null);

        assertThat(policy.evaluateRule(withoutSlot).code()).isEqualTo("ENROLLMENT_FEE_SLOT_REQUIRED");
        assertThat(policy.evaluateRule(withoutTemplate).code()).isEqualTo("ENROLLMENT_FEE_SLOT_REQUIRED");
        assertThat(policy.evaluateRule(complete).allowed()).isTrue();
        assertThat(policy.evaluateRule(installment).allowed()).isTrue();
    }

    @Test
    @Tag("D-097")
    void theChargeCurrencyMustBeTheOfferingCurrency() {
        OfferingCurrencyPolicy policy = new OfferingCurrencyPolicy();

        assertThat(policy.evaluateRule(context(monthly(), Currency.ARS, null, null)).allowed()).isTrue();
        assertThat(policy.evaluateRule(context(monthly(), Currency.USD, null, null)).code()).isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    @Tag("D-009")
    @Tag("D-021")
    void aUsedChargeKeyIsRejectedWithAlreadyIssued() {
        ChargeKeyNotAlreadyUsedPolicy policy = new ChargeKeyNotAlreadyUsedPolicy();

        assertThat(policy.evaluateRule(context(monthly(), Currency.ARS, null, null)).allowed()).isTrue();
        assertThat(policy.evaluateRule(context(monthly(), Currency.ARS, 99L, null)).code()).isEqualTo("ALREADY_ISSUED");
    }

    @ParameterizedTest
    @Tag("D-036")
    @CsvSource({"2,2,true", "3,2,false", "1,2,true"})
    void theSlotMustStillBeConfiguredInTheTemplate(int slot, int configured, boolean allowed) {
        InvoiceIssuingContext context = context(request(FeeType.ENROLLMENT_FEE, InvoiceIssuingReason.MONTHLY_RUN, TODAY, 5L, slot), Currency.ARS, null, configured);

        assertThat(new EnrollmentFeeSlotWithinTemplateCountPolicy().evaluateRule(context).allowed()).isEqualTo(allowed);
        assertThat(new EnrollmentFeeSlotWithinTemplateCountPolicy().evaluateRule(context(request(FeeType.ENROLLMENT_FEE, InvoiceIssuingReason.MONTHLY_RUN, TODAY, 5L, slot),
                Currency.ARS, null, null)).allowed()).isTrue();
    }

    @Test
    @Tag("D-129")
    void aDiscountOfAnotherInvoiceTypeIsDiscarded() {
        DiscountMatchesInvoiceTypePolicy policy = new DiscountMatchesInvoiceTypePolicy();
        var candidate = candidate(DiscountType.PROMOTION, 1, InvoiceType.ENROLLMENT_FEE, "100.00", "10.00");

        assertThat(policy.evaluateRule(new DiscountContest(InvoiceType.MONTHLY_INSTALLMENT, candidate, List.of())).code())
                .isEqualTo("DISCOUNT_NOT_APPLICABLE_TO_INVOICE_TYPE");
        assertThat(policy.evaluateRule(new DiscountContest(InvoiceType.ENROLLMENT_FEE, candidate, List.of())).allowed()).isTrue();
    }

    @Test
    @Tag("D-073")
    void onlyTheBiggestDiscountWinsAndTiesGoToTheLowestSourceId() {
        BestDiscountPerInvoiceTypePolicy policy = new BestDiscountPerInvoiceTypePolicy();
        var big = candidate(DiscountType.SCHOLARSHIP, 5, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "50.00");
        var small = candidate(DiscountType.PROMOTION, 1, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "30.00");
        var tieLowId = candidate(DiscountType.PROMOTION, 2, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "50.00");
        var tieSameIdOtherType = candidate(DiscountType.COHORT_ENTRY, 5, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "50.00");

        assertThat(policy.evaluateRule(new DiscountContest(InvoiceType.MONTHLY_INSTALLMENT, big, List.of(small))).allowed()).isTrue();
        assertThat(policy.evaluateRule(new DiscountContest(InvoiceType.MONTHLY_INSTALLMENT, small, List.of(big))).code()).isEqualTo("DISCOUNT_OUTBID_BY_A_BETTER_ONE");
        assertThat(policy.evaluateRule(new DiscountContest(InvoiceType.MONTHLY_INSTALLMENT, big, List.of(tieLowId))).allowed()).isFalse();
        assertThat(policy.evaluateRule(new DiscountContest(InvoiceType.MONTHLY_INSTALLMENT, tieLowId, List.of(big))).allowed()).isTrue();
        assertThat(policy.evaluateRule(new DiscountContest(InvoiceType.MONTHLY_INSTALLMENT, big, List.of(tieSameIdOtherType))).allowed()).isTrue();
        assertThat(policy.evaluateRule(new DiscountContest(InvoiceType.MONTHLY_INSTALLMENT, tieSameIdOtherType, List.of(big))).allowed()).isFalse();
    }

    @Test
    @Tag("D-157")
    void aScholarshipOnlyCoversMonthlyInstallments() {
        ScholarshipDiscountAppliesPolicy policy = new ScholarshipDiscountAppliesPolicy();

        assertThat(policy.evaluateRule(new ScholarshipDiscountAppliesPolicy.Offer(FeeType.MONTHLY_INSTALLMENT, true)).allowed()).isTrue();
        assertThat(policy.evaluateRule(new ScholarshipDiscountAppliesPolicy.Offer(FeeType.MONTHLY_INSTALLMENT, false)).code())
                .isEqualTo("SCHOLARSHIP_ONLY_FOR_MONTHLY_INSTALLMENTS");
        assertThat(policy.evaluateRule(new ScholarshipDiscountAppliesPolicy.Offer(FeeType.ENROLLMENT_FEE, true)).allowed()).isFalse();
    }

    @ParameterizedTest
    @Tag("D-074")
    @CsvSource({"2,0,true", "2,1,true", "2,2,false", "2,3,false", ",50,true"})
    void aPromotionStopsWhenItsInstallmentsAreExhausted(Integer scope, long applied, boolean allowed) {
        assertThat(new PromotionDiscountInstallmentCapPolicy().evaluateRule(new PromotionDiscountInstallmentCapPolicy.Usage(scope, applied)).allowed()).isEqualTo(allowed);
    }

    @ParameterizedTest
    @Tag("D-018")
    @Tag("D-019")
    @CsvSource({"true,MONTHLY_RUN,50,true", "true,PROGRAM_ADMISSION,50,false", "false,MONTHLY_RUN,50,false", "true,MONTHLY_RUN,0,false", "true,MONTHLY_RUN,,false"})
    void theEntryDiscountAppliesOnlyToTheEntryFeeOutsideAdmission(boolean entry, InvoiceIssuingReason reason, BigDecimal points, boolean allowed) {
        Percentage discount = points == null ? null : Percentage.ofPoints(points);

        assertThat(new CohortEntryDiscountAppliesPolicy().evaluateRule(new CohortEntryDiscountAppliesPolicy.Offer(entry, reason, discount)).allowed()).isEqualTo(allowed);
    }

    @Test
    @Tag("D-097")
    void theEnrollmentMustBelongToTheRecipientAndRegularChargesNeedOne() {
        ChargeRecipientMatchesEnrollmentPolicy policy = new ChargeRecipientMatchesEnrollmentPolicy();
        InvoiceIssuingRequest withEnrollment = monthly();
        InvoiceIssuingRequest withoutEnrollment = new InvoiceIssuingRequest(1L, null, null, 5L, null, null, FeeType.MONTHLY_INSTALLMENT, java.time.YearMonth.of(2027, 3),
                TODAY, TODAY, InvoiceIssuingReason.MONTHLY_RUN, null, null, null, false, null, null, null, null, null, ACTOR);
        InvoiceIssuingRequest admission = new InvoiceIssuingRequest(1L, null, null, 5L, null, null, FeeType.ENROLLMENT_FEE, java.time.YearMonth.of(2027, 3),
                TODAY, TODAY, InvoiceIssuingReason.PROGRAM_ADMISSION, null, 4L, 1, true, null, null, null, null, null, ACTOR);

        assertThat(policy.evaluateRule(context(withEnrollment, Currency.ARS, null, null)).allowed()).isTrue();
        assertThat(policy.evaluateRule(new InvoiceIssuingContext(withEnrollment, new com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingFacts(TODAY,
                Currency.ARS, ars("100.00"), 7L, null, null, new com.red_escuela.backend.billing.invoice.domain.StudentSnapshot("A B", "1", null), false))).code())
                .isEqualTo("ENROLLMENT_RECIPIENT_MISMATCH");
        assertThat(policy.evaluateRule(context(withoutEnrollment, Currency.ARS, null, null)).code()).isEqualTo("ENROLLMENT_REQUIRED");
        assertThat(policy.evaluateRule(context(admission, Currency.ARS, null, null)).allowed()).isTrue();
    }

    @Test
    @Tag("D-050")
    void aCourseNeverIssuesAnEnrollmentFee() {
        InvoiceIssuingRequest courseFee = new InvoiceIssuingRequest(1L, null, null, null, 8L, null, FeeType.ENROLLMENT_FEE, java.time.YearMonth.of(2027, 3),
                TODAY, TODAY, InvoiceIssuingReason.COURSE_ADMISSION, null, 4L, 1, true, null, null, null, null, null, ACTOR);

        assertThat(new InvoiceIssuingReasonMatchesFeeTypePolicy().evaluateRule(context(courseFee, Currency.ARS, null, null)).allowed()).isFalse();
    }
}
