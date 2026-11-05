package com.red_escuela.backend.shared.domain;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Tag;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-099")
@Tag("D-174")
class MonetaryAdjustmentProperties {

    @Provide
    Arbitrary<BigDecimal> nonNegative() {
        return PropertySettings.nonNegativeAmount();
    }

    @Provide
    Arbitrary<BigDecimal> negative() {
        return PropertySettings.negativeAmount();
    }

    @Provide
    Arbitrary<Percentage> percentages() {
        return PropertySettings.anyPercentage();
    }

    @Property(seed = PropertySettings.SEED)
    void aNominalAdjustmentNeverAcceptsANegativeAmount(@ForAll("negative") BigDecimal amount, @ForAll("anyCurrency") Currency currency) {
        assertThatThrownBy(() -> new MonetaryAdjustment.Nominal(Money.of(amount, currency)))
                .isInstanceOf(NegativeAdjustmentAmountException.class);
    }

    @Provide
    Arbitrary<Currency> anyCurrency() {
        return PropertySettings.anyCurrency();
    }

    @Property(seed = PropertySettings.SEED)
    void aNominalAdjustmentAppliesItsAmountIgnoringTheBase(@ForAll("nonNegative") BigDecimal amount, @ForAll("nonNegative") BigDecimal base,
                                                           @ForAll("anyCurrency") Currency currency) {
        MonetaryAdjustment adjustment = new MonetaryAdjustment.Nominal(Money.of(amount, currency));

        assertThat(adjustment.applyTo(Money.of(base, currency))).isEqualTo(Money.of(amount, currency));
        assertThat(adjustment.applyTo(Money.of(base, currency)).isNegative()).isFalse();
    }

    @Property(seed = PropertySettings.SEED)
    void aProportionalAdjustmentIsNeverNegativeNorAboveTheBase(@ForAll("nonNegative") BigDecimal base, @ForAll("percentages") Percentage rate,
                                                               @ForAll("anyCurrency") Currency currency) {
        Money baseMoney = Money.of(base, currency);

        Money applied = new MonetaryAdjustment.Proportional(rate).applyTo(baseMoney);

        assertThat(applied.isNegative()).isFalse();
        assertThat(baseMoney.isGreaterThan(applied) || baseMoney.equals(applied)).isTrue();
        assertThat(applied).isEqualTo(baseMoney.times(rate));
    }

    @Property(seed = PropertySettings.SEED)
    void anAdjustmentInAnotherCurrencyIsRejected(@ForAll("nonNegative") BigDecimal amount, @ForAll("anyCurrency") Currency first,
                                                 @ForAll("anyCurrency") Currency second) {
        net.jqwik.api.Assume.that(first != second);
        MonetaryAdjustment adjustment = new MonetaryAdjustment.Nominal(Money.of(amount, first));

        assertThatThrownBy(() -> adjustment.requireCurrency(second)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> adjustment.applyTo(Money.of(amount, second))).isInstanceOf(CurrencyMismatchException.class);
    }
}
