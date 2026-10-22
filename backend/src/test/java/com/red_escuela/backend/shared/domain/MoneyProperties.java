package com.red_escuela.backend.shared.domain;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-099")
@Tag("D-174")
class MoneyProperties {

    @Provide
    Arbitrary<Money[]> threeMoneysInTheSameCurrency() {
        return PropertySettings.anyCurrency().flatMap(currency ->
                Combinators.combine(PropertySettings.anyMoneyIn(currency), PropertySettings.anyMoneyIn(currency), PropertySettings.anyMoneyIn(currency))
                        .as((a, b, c) -> new Money[]{a, b, c}));
    }

    @Provide
    Arbitrary<Money[]> twoMoneysInDifferentCurrencies() {
        return Combinators.combine(PropertySettings.anyAmount(), PropertySettings.anyAmount(), PropertySettings.anyCurrency(), PropertySettings.anyCurrency())
                .filter((x, y, first, second) -> first != second)
                .as((x, y, first, second) -> new Money[]{Money.of(x, first), Money.of(y, second)});
    }

    @Property(seed = PropertySettings.SEED)
    void sumIsCommutative(@ForAll("threeMoneysInTheSameCurrency") Money[] moneys) {
        assertThat(moneys[0].plus(moneys[1])).isEqualTo(moneys[1].plus(moneys[0]));
    }

    @Property(seed = PropertySettings.SEED)
    void sumIsAssociative(@ForAll("threeMoneysInTheSameCurrency") Money[] moneys) {
        assertThat(moneys[0].plus(moneys[1]).plus(moneys[2])).isEqualTo(moneys[0].plus(moneys[1].plus(moneys[2])));
    }

    @Property(seed = PropertySettings.SEED)
    void addingAndSubtractingTheSameAmountReturnsTheOriginal(@ForAll("threeMoneysInTheSameCurrency") Money[] moneys) {
        assertThat(moneys[0].plus(moneys[1]).minus(moneys[1])).isEqualTo(moneys[0]);
    }

    @Property(seed = PropertySettings.SEED)
    void differentCurrenciesAlwaysMismatch(@ForAll("twoMoneysInDifferentCurrencies") Money[] moneys) {
        assertThatThrownBy(() -> moneys[0].plus(moneys[1])).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> moneys[0].minus(moneys[1])).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> moneys[0].isGreaterThan(moneys[1])).isInstanceOf(CurrencyMismatchException.class);
    }

    @Property(seed = PropertySettings.SEED)
    void timesRoundsHalfUpToTwoDecimalsLikeBigDecimal(@ForAll("threeMoneysInTheSameCurrency") Money[] moneys,
                                                      @ForAll("percentages") Percentage rate) {
        BigDecimal expected = moneys[0].amount().multiply(rate.fraction()).setScale(2, RoundingMode.HALF_UP);

        Money result = moneys[0].times(rate);

        assertThat(result.amount()).isEqualTo(expected);
        assertThat(result.amount().scale()).isEqualTo(2);
        assertThat(result.currency()).isEqualTo(moneys[0].currency());
    }

    @Property(seed = PropertySettings.SEED)
    void timesZeroPercentIsZeroAndTimesOneHundredPercentIsTheSame(@ForAll("threeMoneysInTheSameCurrency") Money[] moneys) {
        assertThat(moneys[0].times(Percentage.ofPoints(BigDecimal.ZERO)).isZero()).isTrue();
        assertThat(moneys[0].times(Percentage.ofPoints(new BigDecimal("100")))).isEqualTo(moneys[0]);
    }

    @Property(seed = PropertySettings.SEED)
    void signAndComparisonAgreeWithTheAmount(@ForAll("threeMoneysInTheSameCurrency") Money[] moneys) {
        assertThat(moneys[0].isNegative()).isEqualTo(moneys[0].amount().signum() < 0);
        assertThat(moneys[0].isZero()).isEqualTo(moneys[0].amount().signum() == 0);
        assertThat(moneys[0].isGreaterThan(moneys[1])).isEqualTo(moneys[0].amount().compareTo(moneys[1].amount()) > 0);
    }

    @Provide
    Arbitrary<Percentage> percentages() {
        return PropertySettings.anyPercentage();
    }

    @Property(seed = PropertySettings.SEED)
    void zeroIsTheIdentityOfTheSum(@ForAll("threeMoneysInTheSameCurrency") Money[] moneys) {
        assertThat(moneys[0].plus(Money.zero(moneys[0].currency()))).isEqualTo(moneys[0]);
    }
}
