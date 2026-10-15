package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-075")
@Tag("D-076")
@Tag("D-099")
@Tag("D-240")
class DiscountSavingCalculatorTest {

    private static NonNegativeMoney ars(String amount) {
        return NonNegativeMoney.of(new BigDecimal(amount), Currency.ARS);
    }

    private static MonetaryAdjustment percent(String points) {
        return new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal(points)));
    }

    private static MonetaryAdjustment fixed(String amount, Currency currency) {
        return new MonetaryAdjustment.Nominal(Money.of(new BigDecimal(amount), currency));
    }

    @Test
    void aPercentageIsAppliedOnTheFinalPrice() {
        assertThat(DiscountSavingCalculator.calculateSavingOnFinalPrice(percent("10"), ars("50000.00"), Optional.empty())).isEqualTo(ars("5000.00"));
    }

    @Test
    void theCapCutsTheSaving() {
        assertThat(DiscountSavingCalculator.calculateSavingOnFinalPrice(percent("20"), ars("80000.00"), Optional.of(ars("12000.00")))).isEqualTo(ars("12000.00"));
    }

    @Test
    void aCapAboveTheSavingChangesNothing() {
        assertThat(DiscountSavingCalculator.calculateSavingOnFinalPrice(percent("10"), ars("80000.00"), Optional.of(ars("12000.00")))).isEqualTo(ars("8000.00"));
    }

    @Test
    void theSavingNeverExceedsTheLineAmount() {
        assertThat(DiscountSavingCalculator.calculateSavingOnFinalPrice(fixed("100000.00", Currency.ARS), ars("80000.00"), Optional.empty())).isEqualTo(ars("80000.00"));
    }

    @Test
    void aSavingEqualToTheLineAmountIsKept() {
        assertThat(DiscountSavingCalculator.calculateSavingOnFinalPrice(percent("100"), ars("80000.00"), Optional.empty())).isEqualTo(ars("80000.00"));
    }

    @Test
    void roundingIsHalfUpToTwoDecimals() {
        assertThat(DiscountSavingCalculator.calculateSavingOnFinalPrice(percent("33.33"), ars("80000.00"), Optional.empty())).isEqualTo(ars("26664.00"));
        assertThat(DiscountSavingCalculator.calculateSavingOnFinalPrice(percent("12.5"), ars("80000.05"), Optional.empty())).isEqualTo(ars("10000.01"));
    }

    @Test
    void aFixedAmountInAnotherCurrencyIsRejected() {
        assertThatThrownBy(() -> DiscountSavingCalculator.calculateSavingOnFinalPrice(fixed("100.00", Currency.USD), ars("80000.00"), Optional.empty()))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void aCapInAnotherCurrencyIsRejected() {
        assertThatThrownBy(() -> DiscountSavingCalculator.calculateSavingOnFinalPrice(percent("10"), ars("80000.00"),
                Optional.of(NonNegativeMoney.of(new BigDecimal("100.00"), Currency.USD)))).isInstanceOf(CurrencyMismatchException.class);
    }
}
