package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReviewPoliciesTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 3, 9);

    private static PaymentVerificationCheck check(Money amount, LocalDate transferDate, String note) {
        return new PaymentVerificationCheck(amount, transferDate, note, Currency.ARS, TODAY);
    }

    private static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-0.01", "10000000000.00"})
    @Tag("D-139")
    void aVerifiedAmountThatIsNotPositiveOrNotStorableIsDenied(String amount) {
        PolicyVerdict verdict = new VerifiedPaymentAmountPolicy().evaluateRule(check(ars(amount), null, null));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("VERIFIED_PAYMENT_AMOUNT_INVALID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "9999999999.99"})
    @Tag("D-139")
    void theSmallestAndTheLargestStorableAmountsAreAllowed(String amount) {
        assertThat(new VerifiedPaymentAmountPolicy().evaluateRule(check(ars(amount), null, null)).allowed()).isTrue();
    }

    @Test
    @Tag("D-139")
    void noVerifiedAmountKeepsTheDeclaredOne() {
        assertThat(new VerifiedPaymentAmountPolicy().evaluateRule(check(null, null, null)).allowed()).isTrue();
        assertThat(new VerifiedPaymentCurrencyPolicy().evaluateRule(check(null, null, null)).allowed()).isTrue();
    }

    @Test
    @Tag("D-097")
    void aVerifiedAmountInAnotherCurrencyThanTheAccountIsDenied() {
        PolicyVerdict verdict = new VerifiedPaymentCurrencyPolicy().evaluateRule(check(Money.of(new BigDecimal("100.00"), Currency.USD), null, null));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("VERIFIED_PAYMENT_CURRENCY_MISMATCH");
        assertThat(new VerifiedPaymentCurrencyPolicy().evaluateRule(check(ars("100.00"), null, null)).allowed()).isTrue();
    }

    @Test
    @Tag("D-169")
    void aVerifiedDateAfterTodayIsDeniedAndTodayOrBeforeIsAllowed() {
        PolicyVerdict future = new VerifiedPaymentDatePolicy().evaluateRule(check(null, TODAY.plusDays(1), null));

        assertThat(future.allowed()).isFalse();
        assertThat(future.code()).isEqualTo("PAYMENT_DATE_IN_FUTURE");
        assertThat(new VerifiedPaymentDatePolicy().evaluateRule(check(null, TODAY, null)).allowed()).isTrue();
        assertThat(new VerifiedPaymentDatePolicy().evaluateRule(check(null, TODAY.minusDays(30), null)).allowed()).isTrue();
        assertThat(new VerifiedPaymentDatePolicy().evaluateRule(check(null, null, null)).allowed()).isTrue();
    }

    @Test
    @Tag("D-139")
    void aReviewNoteOver500CharactersIsDeniedAndOneOf500IsAllowed() {
        PolicyVerdict tooLong = new PaymentReviewNoteLengthPolicy().evaluateRule(check(null, null, "n".repeat(501)));

        assertThat(tooLong.allowed()).isFalse();
        assertThat(tooLong.code()).isEqualTo("PAYMENT_REVIEW_NOTE_TOO_LONG");
        assertThat(new PaymentReviewNoteLengthPolicy().evaluateRule(check(null, null, "n".repeat(500))).allowed()).isTrue();
        assertThat(new PaymentReviewNoteLengthPolicy().evaluateRule(check(null, null, null)).allowed()).isTrue();
    }

    @Test
    @Tag("D-139")
    void aRejectionReasonIsRequiredAndLimitedTo500Characters() {
        PaymentRejectionReasonRequiredPolicy policy = new PaymentRejectionReasonRequiredPolicy();

        assertThat(policy.evaluateRule(new PaymentRejectionCheck(null)).code()).isEqualTo("PAYMENT_REJECTION_REASON_REQUIRED");
        assertThat(policy.evaluateRule(new PaymentRejectionCheck("")).code()).isEqualTo("PAYMENT_REJECTION_REASON_REQUIRED");
        assertThat(policy.evaluateRule(new PaymentRejectionCheck("   ")).code()).isEqualTo("PAYMENT_REJECTION_REASON_REQUIRED");
        assertThat(policy.evaluateRule(new PaymentRejectionCheck("m".repeat(501))).code()).isEqualTo("PAYMENT_REJECTION_REASON_TOO_LONG");
        assertThat(policy.evaluateRule(new PaymentRejectionCheck("m".repeat(500))).allowed()).isTrue();
    }
}
