package com.red_escuela.backend.billing.run.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyInvoiceSimulationRulesTest {

    private static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    @Test
    @Tag("D-453")
    void anIncreaseOfMoreThanFifteenPercentIsAtypical() {
        assertThat(AtypicalInstallmentIncreaseRule.isAtypicalIncrease(ars("1000000.00"), ars("1160000.00"))).isTrue();
    }

    @Test
    @Tag("D-453")
    void anIncreaseOfExactlyFifteenPercentIsNotAtypical() {
        assertThat(AtypicalInstallmentIncreaseRule.isAtypicalIncrease(ars("1000000.00"), ars("1150000.00"))).isFalse();
    }

    @Test
    @Tag("D-453")
    void aDropOrAMissingPreviousTotalIsNotAtypical() {
        assertThat(AtypicalInstallmentIncreaseRule.isAtypicalIncrease(ars("1000000.00"), ars("500000.00"))).isFalse();
        assertThat(AtypicalInstallmentIncreaseRule.isAtypicalIncrease(ars("0.00"), ars("500000.00"))).isFalse();
    }

    @Test
    void aNotConfiguredYearIsAnAlertAndAnOrdinaryNotApplicableIsNot() {
        assertThat(MonthlyInvoiceSimulationOutcome.wouldNotBeIssued(1L, List.of("YEAR_NOT_CONFIGURED")).findAlertType())
                .contains(MonthlyInvoiceSimulationAlertType.YEAR_NOT_CONFIGURED);
        assertThat(MonthlyInvoiceSimulationOutcome.wouldNotBeIssued(1L, List.of("OUTSIDE_TERM")).findAlertType()).isEmpty();
        assertThat(MonthlyInvoiceSimulationOutcome.wouldBeAlreadyIssued(1L).findAlertType()).isEmpty();
    }

    @Test
    void aMissingPriceAndAnyOtherFailureAreDifferentAlerts() {
        assertThat(MonthlyInvoiceSimulationOutcome.wouldFail(1L, "NoEffectivePriceException", "sin precio").findAlertType())
                .contains(MonthlyInvoiceSimulationAlertType.PRICE_MISSING);
        assertThat(MonthlyInvoiceSimulationOutcome.wouldFail(1L, "IllegalStateException", "x").findAlertType())
                .contains(MonthlyInvoiceSimulationAlertType.EMISSION_ERROR);
    }
}
