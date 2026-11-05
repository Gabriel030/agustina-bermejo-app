package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReminderContentTermsTest {
    private static final NonNegativeMoney PRICE = NonNegativeMoney.of(new BigDecimal("100000.00"), Currency.ARS);

    private static LatePaymentTerms terms(String fraction, int graceDays, int suspensionDays, int suspensionInvoices, boolean chargingEnabled) {
        return new LatePaymentTerms(graceDays, Percentage.ofFraction(new BigDecimal(fraction)), suspensionDays, suspensionInvoices, true, 10, true, chargingEnabled);
    }

    @Test
    @Tag("D-345")
    @Tag("D-288")
    void lateFeeTermsCarryTheRateTheGraceAndTheFinalPrice() {
        ReminderLateFeeTerms lateFee = ReminderLateFeeTerms.of(terms("0.0017", 5, 30, 2, true), PRICE).orElseThrow();

        assertThat(lateFee.dailyRate()).isEqualTo(Percentage.ofFraction(new BigDecimal("0.0017")));
        assertThat(lateFee.graceDays()).isEqualTo(5);
        assertThat(lateFee.finalPrice()).isEqualTo(PRICE);
    }

    @Test
    @Tag("D-345")
    void noLateFeeSentenceWhenChargingIsDisabled() {
        assertThat(ReminderLateFeeTerms.of(terms("0.0017", 5, 30, 2, false), PRICE)).isEmpty();
    }

    @Test
    @Tag("D-345")
    void noLateFeeSentenceWhenTheRateIsZeroButTheSmallestRateIsStated() {
        assertThat(ReminderLateFeeTerms.of(terms("0", 5, 30, 2, true), PRICE)).isEmpty();
        assertThat(ReminderLateFeeTerms.of(terms("0.000001", 5, 30, 2, true), PRICE)).isPresent();
    }

    @Test
    @Tag("D-345")
    void lateFeeTermsRejectNegativeGraceAndMissingValues() {
        Percentage rate = Percentage.ofFraction(new BigDecimal("0.0017"));

        assertThatThrownBy(() -> new ReminderLateFeeTerms(rate, -1, PRICE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReminderLateFeeTerms(null, 0, PRICE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReminderLateFeeTerms(rate, 0, null)).isInstanceOf(NullPointerException.class);
        assertThat(new ReminderLateFeeTerms(rate, 0, PRICE).graceDays()).isZero();
    }

    @Test
    @Tag("D-345")
    @Tag("D-306")
    void theSuspensionRuleFollowsTheThresholdsInForce() {
        ReminderSuspensionRule rule = ReminderSuspensionRule.of(terms("0.0017", 5, 30, 2, true)).orElseThrow();

        assertThat(rule.afterDays()).isEqualTo(30);
        assertThat(rule.afterInvoiceCount()).isEqualTo(2);
    }

    @Test
    @Tag("D-345")
    @Tag("D-306")
    void theInitialConfigurationOfImmediateSuspensionStatesNoRule() {
        assertThat(ReminderSuspensionRule.of(LatePaymentTerms.initial())).isEmpty();
        assertThat(ReminderSuspensionRule.of(terms("0.0017", 5, 1, 1, true))).isPresent();
    }

    @Test
    @Tag("D-306")
    void suspensionRuleRejectsNegativeThresholds() {
        assertThatThrownBy(() -> new ReminderSuspensionRule(-1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReminderSuspensionRule(1, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new ReminderSuspensionRule(0, 0).afterDays()).isZero();
    }

    @Test
    @Tag("D-345")
    void contentTermsWithoutDataStateNothing() {
        ReminderContentTerms none = ReminderContentTerms.none();

        assertThat(none.findLateFeeOf(1L)).isEmpty();
        assertThat(none.findSuspensionRule()).isEmpty();
    }
}
