package com.red_escuela.backend.shared.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MonetaryAdjustmentCurrencyTest {

    private final MonetaryAdjustment nominalInArs = new MonetaryAdjustment.Nominal(Money.of(new BigDecimal("2000.00"), Currency.ARS));

    @Test
    void aNominalAdjustmentInADifferentCurrencyIsRejected() {
        assertThrows(CurrencyMismatchException.class, () -> nominalInArs.requireCurrency(Currency.USD));
    }

    @Test
    void aNominalAdjustmentInTheSameCurrencyIsAccepted() {
        assertDoesNotThrow(() -> nominalInArs.requireCurrency(Currency.ARS));
    }

    @Test
    void aProportionalAdjustmentAcceptsAnyCurrency() {
        MonetaryAdjustment proportional = new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal("10")));
        assertDoesNotThrow(() -> proportional.requireCurrency(Currency.USD));
    }
}
