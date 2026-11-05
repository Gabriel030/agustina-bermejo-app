package com.red_escuela.backend.shared.domain;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-099")
@Tag("D-171")
@Tag("D-174")
class NonNegativeMoneyProperties {

    @Provide
    Arbitrary<BigDecimal> nonNegative() {
        return PropertySettings.nonNegativeAmount();
    }

    @Provide
    Arbitrary<BigDecimal> negative() {
        return PropertySettings.negativeAmount();
    }

    @Provide
    Arbitrary<Currency> anyCurrency() {
        return PropertySettings.anyCurrency();
    }

    @Provide
    Arbitrary<Percentage> percentages() {
        return PropertySettings.anyPercentage();
    }

    @Property(seed = PropertySettings.SEED)
    void anyAmountFromZeroUpIsAccepted(@ForAll("nonNegative") BigDecimal amount, @ForAll("anyCurrency") Currency currency) {
        assertThat(NonNegativeMoney.of(amount, currency).value()).isEqualTo(Money.of(amount, currency));
    }

    @Property(seed = PropertySettings.SEED)
    void anyNegativeAmountIsRejected(@ForAll("negative") BigDecimal amount, @ForAll("anyCurrency") Currency currency) {
        assertThatThrownBy(() -> NonNegativeMoney.of(amount, currency)).isInstanceOf(NegativeMoneyAmountException.class);
        assertThatThrownBy(() -> NonNegativeMoney.of(Money.of(amount, currency))).isInstanceOf(NegativeMoneyAmountException.class);
    }

    @Property(seed = PropertySettings.SEED)
    void sumIsNeverNegativeAndIsCommutative(@ForAll("nonNegative") BigDecimal first, @ForAll("nonNegative") BigDecimal second,
                                            @ForAll("anyCurrency") Currency currency) {
        NonNegativeMoney a = NonNegativeMoney.of(first, currency);
        NonNegativeMoney b = NonNegativeMoney.of(second, currency);

        assertThat(a.plus(b).value().isNegative()).isFalse();
        assertThat(a.plus(b)).isEqualTo(b.plus(a));
        assertThat(a.plus(NonNegativeMoney.zero(currency))).isEqualTo(a);
    }

    @Property(seed = PropertySettings.SEED)
    void timesIsNeverNegative(@ForAll("nonNegative") BigDecimal amount, @ForAll("percentages") Percentage rate,
                              @ForAll("anyCurrency") Currency currency) {
        NonNegativeMoney result = NonNegativeMoney.of(amount, currency).times(rate);

        assertThat(result.value().isNegative()).isFalse();
        assertThat(result.value()).isEqualTo(Money.of(amount, currency).times(rate));
    }

    @Property(seed = PropertySettings.SEED)
    void minusNotBelowZeroIsTheDifferenceFlooredAtZero(@ForAll("nonNegative") BigDecimal first, @ForAll("nonNegative") BigDecimal second,
                                                       @ForAll("anyCurrency") Currency currency) {
        NonNegativeMoney a = NonNegativeMoney.of(first, currency);
        NonNegativeMoney b = NonNegativeMoney.of(second, currency);
        BigDecimal expected = first.subtract(second).max(BigDecimal.ZERO);

        assertThat(a.minusNotBelowZero(b).value().amount()).isEqualByComparingTo(expected);
        assertThat(a.minus(b)).isEqualTo(Money.of(first.subtract(second), currency));
    }

    @Property(seed = PropertySettings.SEED)
    void operatingAcrossCurrenciesIsRejected(@ForAll("nonNegative") BigDecimal amount, @ForAll("anyCurrency") Currency first,
                                             @ForAll("anyCurrency") Currency second) {
        net.jqwik.api.Assume.that(first != second);
        NonNegativeMoney a = NonNegativeMoney.of(amount, first);
        NonNegativeMoney b = NonNegativeMoney.of(amount, second);

        assertThatThrownBy(() -> a.plus(b)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> a.minus(b)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> a.minusNotBelowZero(b)).isInstanceOf(CurrencyMismatchException.class);
    }
}
