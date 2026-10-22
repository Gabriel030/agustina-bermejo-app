package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;
import net.jqwik.api.Combinators;

import java.math.BigDecimal;
import java.util.List;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-144")
class CurrentAccountBalanceProperties {

    record Movement(CurrentAccountEntryDirection direction, BigDecimal amount) {
    }

    @Provide
    Arbitrary<List<Movement>> movements() {
        Arbitrary<Movement> movement = Combinators.combine(
                Arbitraries.of(CurrentAccountEntryDirection.class),
                Arbitraries.bigDecimals().between(new BigDecimal("0.01"), new BigDecimal("500000.00")).ofScale(2)).as(Movement::new);
        return movement.list().ofMaxSize(60);
    }

    @Property(seed = "20261007")
    void theSignedBalanceIsTheSumOfCreditsMinusTheSumOfDebitsAndZeroSideMeansZeroAmount(@ForAll("movements") List<Movement> sequence) {
        CurrentAccountBalance balance = CurrentAccountBalance.zero(Currency.ARS);
        BigDecimal expected = BigDecimal.ZERO;

        for (Movement movement : sequence) {
            balance = balance.applyEntry(movement.direction(), ars(movement.amount().toPlainString()));
            expected = movement.direction() == CurrentAccountEntryDirection.CREDIT ? expected.add(movement.amount()) : expected.subtract(movement.amount());

            assertThat(balance.toSignedAmount()).isEqualTo(Money.of(expected, Currency.ARS));
            assertThat(balance.side() == CurrentAccountBalanceSide.ZERO).isEqualTo(balance.amount().value().isZero());
            assertThat(balance.side()).isEqualTo(expected.signum() < 0 ? CurrentAccountBalanceSide.OWES
                    : expected.signum() == 0 ? CurrentAccountBalanceSide.ZERO : CurrentAccountBalanceSide.IN_FAVOR);
        }
    }
}
