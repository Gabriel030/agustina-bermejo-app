package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.discount.exception.IllegalDiscountDefinitionTransitionException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscountQuotaCostEstimateTest {

    @Test
    void anEstimatedCostCarriesItsAmountAndAWithoutCostEstimateCarriesNone() {
        NonNegativeMoney cost = NonNegativeMoney.of(new BigDecimal("1500.00"), Currency.ARS);

        DiscountQuotaCostEstimate estimated = DiscountQuotaCostEstimate.estimated(cost);
        DiscountQuotaCostEstimate without = DiscountQuotaCostEstimate.withoutCost(DiscountQuotaCostStatus.NO_QUOTA);

        assertThat(estimated.status()).isEqualTo(DiscountQuotaCostStatus.ESTIMATED);
        assertThat(estimated.cost()).isEqualTo(Optional.of(cost));
        assertThat(without.status()).isEqualTo(DiscountQuotaCostStatus.NO_QUOTA);
        assertThat(without.cost()).isEmpty();
    }

    @Test
    void onlyAnActiveDefinitionCanBeClosed() {
        assertThatCode(() -> DiscountDefinitionStateMachine.requireClosableFrom(DiscountDefinitionStatus.ACTIVE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> DiscountDefinitionStateMachine.requireClosableFrom(DiscountDefinitionStatus.CLOSED)).isInstanceOf(IllegalDiscountDefinitionTransitionException.class);
    }
}
