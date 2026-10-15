package com.red_escuela.backend.shared.domain;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-171")
class NonNegativeMoneyTest {

    private final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void zeroAndPositiveAmountsAreAccepted() {
        assertThat(NonNegativeMoney.zero(Currency.ARS).value()).isEqualTo(Money.zero(Currency.ARS));
        assertThat(NonNegativeMoney.of(new BigDecimal("0.00"), Currency.USD).value().isZero()).isTrue();
        assertThat(NonNegativeMoney.of(new BigDecimal("0.01"), Currency.ARS).value().amount()).isEqualTo(new BigDecimal("0.01"));
    }

    @Test
    void aNegativeCentIsRejectedWithItsExceptionAndAHttp400() {
        assertThatThrownBy(() -> NonNegativeMoney.of(new BigDecimal("-0.01"), Currency.ARS))
                .isInstanceOf(NegativeMoneyAmountException.class)
                .hasMessage("Money amount must not be negative");
        assertThat(new NegativeMoneyAmountException()).isInstanceOf(com.red_escuela.backend.shared.exception.BadRequestException.class);
    }

    @Test
    void anAmountThatRoundsToZeroIsAccepted() {
        assertThat(NonNegativeMoney.of(new BigDecimal("-0.004"), Currency.ARS).value().isZero()).isTrue();
    }

    @Test
    void nullsAreRejected() {
        assertThatThrownBy(() -> new NonNegativeMoney(null)).isInstanceOf(NullPointerException.class).hasMessage("value");
        assertThatThrownBy(() -> NonNegativeMoney.zero(Currency.ARS).plus(null)).isInstanceOf(NullPointerException.class).hasMessage("other");
        assertThatThrownBy(() -> NonNegativeMoney.zero(Currency.ARS).minus(null)).isInstanceOf(NullPointerException.class).hasMessage("other");
        assertThatThrownBy(() -> NonNegativeMoney.zero(Currency.ARS).minusNotBelowZero(null)).isInstanceOf(NullPointerException.class).hasMessage("other");
    }

    @Test
    void subtractingMoreThanTheBalanceReturnsANegativeMoneyOrZeroWhenFlooredAtZero() {
        NonNegativeMoney small = NonNegativeMoney.of(new BigDecimal("10.00"), Currency.ARS);
        NonNegativeMoney big = NonNegativeMoney.of(new BigDecimal("25.50"), Currency.ARS);

        assertThat(small.minus(big)).isEqualTo(Money.of(new BigDecimal("-15.50"), Currency.ARS));
        assertThat(small.minusNotBelowZero(big)).isEqualTo(NonNegativeMoney.zero(Currency.ARS));
        assertThat(big.minusNotBelowZero(small).value()).isEqualTo(Money.of(new BigDecimal("15.50"), Currency.ARS));
        assertThat(small.minusNotBelowZero(small)).isEqualTo(NonNegativeMoney.zero(Currency.ARS));
    }

    @Test
    void timesAppliesThePercentage() {
        NonNegativeMoney base = NonNegativeMoney.of(new BigDecimal("80000.00"), Currency.ARS);

        assertThat(base.times(Percentage.ofPoints(new BigDecimal("5"))).value().amount()).isEqualTo(new BigDecimal("4000.00"));
    }

    @Test
    void serializesLikeMoneyAndReadsBack() throws Exception {
        NonNegativeMoney money = NonNegativeMoney.of(new BigDecimal("1234.50"), Currency.USD);

        String json = objectMapper.writeValueAsString(money);

        assertThat(json).isEqualTo(objectMapper.writeValueAsString(money.value()));
        assertThat(objectMapper.readValue(json, NonNegativeMoney.class)).isEqualTo(money);
        assertThatThrownBy(() -> objectMapper.readValue("{\"amount\":-1.00,\"currency\":\"ARS\"}", NonNegativeMoney.class))
                .hasRootCauseInstanceOf(NegativeMoneyAmountException.class);
    }
}
