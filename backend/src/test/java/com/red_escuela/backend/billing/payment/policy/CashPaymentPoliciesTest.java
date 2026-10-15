package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.CashPaymentLimits;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CashPaymentPoliciesTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 15);

    private static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    private static CashPaymentCheck check(String amount, LocalDate paidOn, boolean director, String reason, String receipt, String key) {
        return new CashPaymentCheck(ars(amount), paidOn, TODAY, director, reason, receipt, key);
    }

    private static CashPaymentCheck dated(LocalDate paidOn, boolean director, String reason) {
        return check("100.00", paidOn, director, reason, null, "key");
    }

    @Test
    @Tag("D-169")
    void aFutureDateIsDeniedAndTodayIsAllowed() {
        CashPaymentDateNotInFuturePolicy policy = new CashPaymentDateNotInFuturePolicy();

        assertThat(policy.evaluateRule(dated(TODAY.plusDays(1), true, null)).code()).isEqualTo("PAYMENT_DATE_IN_FUTURE");
        assertThat(policy.evaluateRule(dated(TODAY, true, null)).allowed()).isTrue();
        assertThat(policy.evaluateRule(dated(TODAY.minusDays(30), true, null)).allowed()).isTrue();
    }

    @ParameterizedTest
    @Tag("D-266")
    @CsvSource({"0,true", "1,true", "5,true", "6,false", "7,false", "30,false"})
    void theAdministrativeWindowReachesExactlyFiveDaysBack(int daysBack, boolean allowed) {
        PolicyVerdict verdict = new CashPaymentDateWithinAdministrativeWindowPolicy().evaluateRule(dated(TODAY.minusDays(daysBack), false, null));

        assertThat(verdict.allowed()).isEqualTo(allowed);
        assertThat(allowed || "CASH_PAYMENT_DATE_TOO_OLD".equals(verdict.code())).isTrue();
    }

    @ParameterizedTest
    @Tag("D-266")
    @Tag("D-270")
    @CsvSource({"0", "5", "6", "400"})
    void theDirectorIsNotLimitedByTheAdministrativeWindow(int daysBack) {
        assertThat(new CashPaymentDateWithinAdministrativeWindowPolicy().evaluateRule(dated(TODAY.minusDays(daysBack), true, "motivo")).allowed()).isTrue();
    }

    @Test
    @Tag("D-266")
    void theDirectorNeedsAReasonOnlyBeyondFiveDays() {
        CashPaymentBackdateReasonPolicy policy = new CashPaymentBackdateReasonPolicy();

        assertThat(policy.evaluateRule(dated(TODAY.minusDays(5), true, null)).allowed()).isTrue();
        assertThat(policy.evaluateRule(dated(TODAY.minusDays(6), true, null)).code()).isEqualTo("CASH_PAYMENT_BACKDATE_REASON_REQUIRED");
        assertThat(policy.evaluateRule(dated(TODAY.minusDays(6), true, "   ")).code()).isEqualTo("CASH_PAYMENT_BACKDATE_REASON_REQUIRED");
        assertThat(policy.evaluateRule(dated(TODAY.minusDays(6), true, "Recibo cargado tarde")).allowed()).isTrue();
        assertThat(policy.evaluateRule(dated(TODAY.minusDays(6), false, null)).allowed()).isTrue();
    }

    @Test
    @Tag("D-266")
    void theReasonRespectsTheFiveHundredCharacterColumn() {
        CashPaymentBackdateReasonPolicy policy = new CashPaymentBackdateReasonPolicy();

        assertThat(policy.evaluateRule(dated(TODAY.minusDays(6), true, "a".repeat(CashPaymentLimits.MAX_BACKDATE_REASON_LENGTH))).allowed()).isTrue();
        assertThat(policy.evaluateRule(dated(TODAY.minusDays(6), true, "a".repeat(CashPaymentLimits.MAX_BACKDATE_REASON_LENGTH + 1))).allowed()).isFalse();
        assertThat(policy.evaluateRule(dated(TODAY, true, "a".repeat(CashPaymentLimits.MAX_BACKDATE_REASON_LENGTH + 1))).allowed()).isFalse();
    }

    @Test
    @Tag("D-136")
    void theIdempotencyKeyFitsTheHundredCharacterColumn() {
        IdempotencyKeyFormatPolicy policy = new IdempotencyKeyFormatPolicy();

        assertThat(policy.evaluateRule(check("100.00", TODAY, false, null, null, "k".repeat(100))).allowed()).isTrue();
        assertThat(policy.evaluateRule(check("100.00", TODAY, false, null, null, "k".repeat(101))).code()).isEqualTo("IDEMPOTENCY_KEY_INVALID");
        assertThat(policy.evaluateRule(check("100.00", TODAY, false, null, null, null)).allowed()).isFalse();
        assertThat(policy.evaluateRule(check("100.00", TODAY, false, null, null, " ")).allowed()).isFalse();
    }

    @Test
    @Tag("D-136")
    void theAmountIsPositiveAndFitsTheTwelveDigitColumn() {
        CashPaymentAmountPolicy policy = new CashPaymentAmountPolicy();

        assertThat(policy.evaluateRule(check("0.01", TODAY, false, null, null, "key")).allowed()).isTrue();
        assertThat(policy.evaluateRule(check("9999999999.99", TODAY, false, null, null, "key")).allowed()).isTrue();
        assertThat(policy.evaluateRule(check("10000000000.00", TODAY, false, null, null, "key")).code()).isEqualTo("CASH_PAYMENT_AMOUNT_INVALID");
        assertThat(policy.evaluateRule(check("0.00", TODAY, false, null, null, "key")).allowed()).isFalse();
        assertThat(policy.evaluateRule(check("-5.00", TODAY, false, null, null, "key")).allowed()).isFalse();
    }

    @Test
    @Tag("D-136")
    void theReceiptNumberIsOptionalAndBounded() {
        CashPaymentReceiptNumberPolicy policy = new CashPaymentReceiptNumberPolicy();

        assertThat(policy.evaluateRule(check("100.00", TODAY, false, null, null, "key")).allowed()).isTrue();
        assertThat(policy.evaluateRule(check("100.00", TODAY, false, null, "r".repeat(CashPaymentLimits.MAX_RECEIPT_NUMBER_LENGTH), "key")).allowed()).isTrue();
        assertThat(policy.evaluateRule(check("100.00", TODAY, false, null, "r".repeat(CashPaymentLimits.MAX_RECEIPT_NUMBER_LENGTH + 1), "key")).code())
                .isEqualTo("CASH_PAYMENT_RECEIPT_NUMBER_INVALID");
    }

    @Test
    @Tag("D-319")
    @Tag("D-207")
    void aPreviewIsCurrentOnlyWhenItsFingerprintMatchesTheRecomputedOne() {
        CashPaymentPreviewIsCurrentPolicy policy = new CashPaymentPreviewIsCurrentPolicy();

        assertThat(policy.evaluateRule(new CashPaymentPreviewCheck("abc", "abc")).allowed()).isTrue();
        assertThat(policy.evaluateRule(new CashPaymentPreviewCheck("abc", "abd")).code()).isEqualTo("CASH_PAYMENT_PREVIEW_OUTDATED");
        assertThat(policy.evaluateRule(new CashPaymentPreviewCheck(null, "abc")).allowed()).isFalse();
    }
}
