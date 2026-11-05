package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTextTest {

    @Test
    void wholeAmountsHaveNoDecimalsAndUseArgentineThousandsSeparators() {
        assertThat(MoneyText.of(Money.of(new BigDecimal("120000.00"), Currency.ARS))).isEqualTo("$120.000");
        assertThat(MoneyText.of(Money.of(new BigDecimal("40000"), Currency.ARS))).isEqualTo("$40.000");
        assertThat(MoneyText.of(Money.zero(Currency.ARS))).isEqualTo("$0");
    }

    @Test
    void fractionalAmountsKeepTwoDecimalsWithACommaAndDollarsAreMarked() {
        assertThat(MoneyText.of(Money.of(new BigDecimal("1234.50"), Currency.ARS))).isEqualTo("$1.234,50");
        assertThat(MoneyText.of(Money.of(new BigDecimal("99.05"), Currency.USD))).isEqualTo("US$99,05");
    }
}
