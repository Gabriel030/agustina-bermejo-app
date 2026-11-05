package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.PaymentMethodType;
import com.red_escuela.backend.billing.payment.domain.RefundDestination;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefundPoliciesTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 4, 15);
    private static final LocalDate REQUESTED_ON = LocalDate.of(2027, 4, 10);
    private static final RefundDestination DESTINATION = new RefundDestination("0110000000000000000001", "Ana Pérez");

    private static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    private static NonNegativeMoney nonNegative(String amount) {
        return NonNegativeMoney.of(new BigDecimal(amount), Currency.ARS);
    }

    private static PolicyVerdict amount(String amount) {
        return new RefundAmountPolicy().evaluateRule(new RefundRequestCheck(ars(amount), DESTINATION));
    }

    private static PolicyVerdict destination(RefundDestination destination) {
        return new RefundDestinationFormatPolicy().evaluateRule(new RefundRequestCheck(ars("10.00"), destination));
    }

    private static RefundAvailabilityCheck availability(String requested, boolean overdue, String credit, String pending, String refundable) {
        return new RefundAvailabilityCheck(ars(requested), overdue, nonNegative(credit), nonNegative(pending), nonNegative(refundable));
    }

    private static RefundCompletionCheck completion(PaymentMethodType original, PaymentMethodType declared, LocalDate completedOn, String reference) {
        return new RefundCompletionCheck(original, declared, completedOn, REQUESTED_ON, TODAY, reference);
    }

    @Tag("D-159")
    @ParameterizedTest
    @CsvSource({"0.01,true", "9999999999.99,true", "10000000000.00,false", "0.00,false", "-1.00,false"})
    void theAmountMustBePositiveAndFitTheColumn(String amount, boolean allowed) {
        assertThat(amount(amount).allowed()).isEqualTo(allowed);
        if (!allowed) {
            assertThat(amount(amount).code()).isEqualTo("REFUND_AMOUNT_INVALID");
        }
    }

    @Tag("D-159")
    @Test
    void aMissingAmountIsInvalidInsteadOfFailing() {
        assertThat(new RefundAmountPolicy().evaluateRule(new RefundRequestCheck(null, DESTINATION)).allowed()).isFalse();
    }

    @Tag("D-267")
    @Test
    void theDestinationAtTheColumnLimitsIsAcceptedAndOneCharacterMoreIsInvalid() {
        assertThat(destination(new RefundDestination("a".repeat(50), "b".repeat(120))).allowed()).isTrue();
        assertThat(destination(new RefundDestination("a".repeat(51), "b")).code()).isEqualTo("REFUND_DESTINATION_INVALID");
        assertThat(destination(new RefundDestination("a", "b".repeat(121))).code()).isEqualTo("REFUND_DESTINATION_INVALID");
    }

    @Tag("D-267")
    @Test
    void theRequestAlwaysNeedsACompleteDestination() {
        assertThat(destination(null).code()).isEqualTo("REFUND_DESTINATION_REQUIRED");
        assertThat(destination(RefundDestination.none()).code()).isEqualTo("REFUND_DESTINATION_REQUIRED");
        assertThat(destination(new RefundDestination("alias", null)).code()).isEqualTo("REFUND_DESTINATION_REQUIRED");
        assertThat(destination(new RefundDestination("  ", "Ana")).code()).isEqualTo("REFUND_DESTINATION_REQUIRED");
    }

    @Tag("D-267")
    @Test
    void everyPartNeedsACompleteDestination() {
        RefundDestinationRequiredPolicy policy = new RefundDestinationRequiredPolicy();

        assertThat(policy.evaluateRule(new RefundDestinationCheck(List.of(DESTINATION, DESTINATION))).allowed()).isTrue();
        assertThat(policy.evaluateRule(new RefundDestinationCheck(List.of(DESTINATION, RefundDestination.none()))).code())
                .isEqualTo("REFUND_DESTINATION_REQUIRED");
        assertThat(policy.evaluateRule(new RefundDestinationCheck(List.of(DESTINATION, new RefundDestination("alias", null)))).allowed()).isFalse();
    }

    @Tag("D-264")
    @Test
    void overdueDebtBlocksTheRefund() {
        RefundWithoutOverdueDebtPolicy policy = new RefundWithoutOverdueDebtPolicy();

        assertThat(policy.evaluateRule(availability("10.00", false, "100.00", "0.00", "100.00")).allowed()).isTrue();
        assertThat(policy.evaluateRule(availability("10.00", true, "100.00", "0.00", "100.00")).code()).isEqualTo("REFUND_BLOCKED_BY_OVERDUE_DEBT");
    }

    @Tag("D-159")
    @Tag("D-264")
    @Test
    void theRequestedAmountIsLimitedByTheCreditWithoutThePendingPartsAndByWhatThePaymentsCanReturn() {
        RefundNotGreaterThanAvailablePolicy policy = new RefundNotGreaterThanAvailablePolicy();

        assertThat(policy.evaluateRule(availability("70.00", false, "100.00", "30.00", "100.00")).allowed()).isTrue();
        assertThat(policy.evaluateRule(availability("70.01", false, "100.00", "30.00", "100.00")).code()).isEqualTo("INSUFFICIENT_ACCOUNT_CREDIT");
        assertThat(policy.evaluateRule(availability("100.00", false, "100.00", "0.00", "99.99")).code()).isEqualTo("REFUND_EXCEEDS_AVAILABLE_AMOUNT");
        assertThat(policy.evaluateRule(availability("100.00", false, "100.00", "0.00", "100.00")).allowed()).isTrue();
    }

    @Tag("D-159")
    @Test
    void pendingPartsAboveTheCreditLeaveNothingAvailable() {
        RefundNotGreaterThanAvailablePolicy policy = new RefundNotGreaterThanAvailablePolicy();

        assertThat(policy.evaluateRule(availability("0.01", false, "50.00", "80.00", "100.00")).code()).isEqualTo("INSUFFICIENT_ACCOUNT_CREDIT");
    }

    @Tag("D-159")
    @ParameterizedTest
    @CsvSource({"2027-04-15,true", "2027-04-10,true", "2027-04-16,false", "2027-04-09,false"})
    void theCompletionDateCannotBeFutureNorBeforeTheRequest(String date, boolean allowed) {
        PolicyVerdict verdict = new RefundCompletionPolicy().evaluateRule(completion(PaymentMethodType.CASH, PaymentMethodType.CASH, LocalDate.parse(date), "REF"));

        assertThat(verdict.allowed()).isEqualTo(allowed);
    }

    @Tag("D-159")
    @Test
    void theCompletionReferenceIsRequiredAndLimitedTo120Characters() {
        RefundCompletionPolicy policy = new RefundCompletionPolicy();

        assertThat(policy.evaluateRule(completion(PaymentMethodType.CASH, PaymentMethodType.CASH, TODAY, "r".repeat(120))).allowed()).isTrue();
        assertThat(policy.evaluateRule(completion(PaymentMethodType.CASH, PaymentMethodType.CASH, TODAY, "r".repeat(121))).code()).isEqualTo("REFUND_COMPLETION_INVALID");
        assertThat(policy.evaluateRule(completion(PaymentMethodType.CASH, PaymentMethodType.CASH, TODAY, "  ")).allowed()).isFalse();
        assertThat(policy.evaluateRule(completion(PaymentMethodType.CASH, PaymentMethodType.CASH, TODAY, null)).allowed()).isFalse();
        assertThat(policy.evaluateRule(completion(PaymentMethodType.CASH, PaymentMethodType.CASH, null, "REF")).allowed()).isFalse();
    }

    @Tag("D-159")
    @Tag("D-209")
    @Test
    void thePartIsCompletedByTheMethodOfItsOriginalPayment() {
        RefundSameMethodAsOriginalPolicy policy = new RefundSameMethodAsOriginalPolicy();

        assertThat(policy.evaluateRule(completion(PaymentMethodType.BANK_TRANSFER, PaymentMethodType.BANK_TRANSFER, TODAY, "REF")).allowed()).isTrue();
        assertThat(policy.evaluateRule(completion(PaymentMethodType.BANK_TRANSFER, PaymentMethodType.CASH, TODAY, "REF")).code()).isEqualTo("REFUND_METHOD_MISMATCH");
        assertThat(policy.evaluateRule(completion(PaymentMethodType.CASH, null, TODAY, "REF")).allowed()).isFalse();
    }

    @Tag("D-159")
    @ParameterizedTest
    @ValueSource(ints = {1, 500})
    void aFailureReasonUpTo500CharactersIsAccepted(int length) {
        assertThat(new RefundFailureReasonPolicy().evaluateRule(new RefundFailureCheck("m".repeat(length))).allowed()).isTrue();
    }

    @Tag("D-159")
    @Test
    void aBlankMissingOrTooLongFailureReasonIsInvalid() {
        RefundFailureReasonPolicy policy = new RefundFailureReasonPolicy();

        assertThat(policy.evaluateRule(new RefundFailureCheck("m".repeat(501))).code()).isEqualTo("REFUND_FAILURE_REASON_INVALID");
        assertThat(policy.evaluateRule(new RefundFailureCheck(" ")).allowed()).isFalse();
        assertThat(policy.evaluateRule(new RefundFailureCheck(null)).allowed()).isFalse();
    }
}
