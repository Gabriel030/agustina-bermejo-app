package com.red_escuela.backend.billing.reporting.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyRatioTest {

    private static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    @Test
    void twoHundredThousandOverTwoMillionIsTenPercent() {
        assertThat(MoneyRatio.of(ars("200000.00"), ars("2000000.00")).orElseThrow().points()).isEqualByComparingTo("10.00");
    }

    @Test
    void aZeroWholeHasNoRatio() {
        assertThat(MoneyRatio.of(ars("0.00"), ars("0.00"))).isEmpty();
    }

    @Test
    void aPartLargerThanTheWholeIsCappedAtOneHundredPercent() {
        assertThat(MoneyRatio.of(ars("150.00"), ars("100.00")).orElseThrow().points()).isEqualByComparingTo("100.00");
    }

    @Test
    @Tag("D-097")
    void amountsInDifferentCurrenciesAreNeverCompared() {
        assertThatThrownBy(() -> MoneyRatio.of(ars("1.00"), Money.of(BigDecimal.TEN, Currency.USD))).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void theRatioIsRoundedHalfUpToSixDecimals() {
        assertThat(MoneyRatio.of(ars("1.00"), ars("3.00")).orElseThrow().fraction()).isEqualByComparingTo("0.333333");
        assertThat(MoneyRatio.of(ars("2.00"), ars("3.00")).orElseThrow().fraction()).isEqualByComparingTo("0.666667");
    }
}
