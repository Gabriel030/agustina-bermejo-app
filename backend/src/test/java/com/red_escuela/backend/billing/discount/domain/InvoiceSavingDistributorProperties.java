package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-295")
@Tag("D-099")
class InvoiceSavingDistributorProperties {

    private static List<NonNegativeMoney> basesOf(List<Long> cents) {
        return cents.stream().map(value -> NonNegativeMoney.of(BigDecimal.valueOf(value, 2), Currency.ARS)).toList();
    }

    private static BigDecimal sumOf(List<NonNegativeMoney> amounts) {
        return amounts.stream().map(amount -> amount.value().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Optional<NonNegativeMoney> capOf(Long capCents) {
        return capCents == null ? Optional.empty() : Optional.of(NonNegativeMoney.of(BigDecimal.valueOf(capCents, 2), Currency.ARS));
    }

    @Property(seed = "20271028")
    void aFixedAmountIsSplitWithoutLosingACentAndWithinTheCapAndTheBase(@ForAll @Size(min = 1, max = 10) List<@LongRange(min = 1, max = 99_999_999) Long> baseCents,
                                                                         @ForAll @LongRange(min = 1, max = 2_000_000_000) long fixedCents,
                                                                         @ForAll @LongRange(min = 1, max = 2_000_000_000) long capCents, @ForAll boolean withCap) {
        List<NonNegativeMoney> bases = basesOf(baseCents);
        MonetaryAdjustment fixed = new MonetaryAdjustment.Nominal(Money.of(BigDecimal.valueOf(fixedCents, 2), Currency.ARS));
        Optional<NonNegativeMoney> cap = capOf(withCap ? capCents : null);

        List<NonNegativeMoney> shares = InvoiceSavingDistributor.distributeInvoiceSavingAcrossLines(fixed, cap, bases);

        BigDecimal expectedTotal = BigDecimal.valueOf(fixedCents, 2).min(sumOf(bases));
        if (withCap) {
            expectedTotal = expectedTotal.min(BigDecimal.valueOf(capCents, 2));
        }
        assertThat(shares).hasSameSizeAs(bases);
        assertThat(sumOf(shares)).isEqualByComparingTo(expectedTotal);
        assertThat(sumOf(shares)).isLessThanOrEqualTo(sumOf(bases));
        for (int index = 0; index < shares.size(); index++) {
            assertThat(shares.get(index).value().amount()).isLessThanOrEqualTo(bases.get(index).value().amount());
            assertThat(shares.get(index).value().amount().scale()).isLessThanOrEqualTo(2);
        }
    }

    @Property(seed = "20271028")
    void aPercentageWithoutCapKeepsTheSavingOfEveryLine(@ForAll @Size(min = 1, max = 10) List<@LongRange(min = 1, max = 99_999_999) Long> baseCents,
                                                       @ForAll @IntRange(min = 1, max = 10000) int percentInHundredths) {
        List<NonNegativeMoney> bases = basesOf(baseCents);
        MonetaryAdjustment percent = new MonetaryAdjustment.Proportional(Percentage.ofPoints(BigDecimal.valueOf(percentInHundredths, 2)));

        List<NonNegativeMoney> shares = InvoiceSavingDistributor.distributeInvoiceSavingAcrossLines(percent, Optional.empty(), bases);

        for (int index = 0; index < shares.size(); index++) {
            assertThat(shares.get(index)).isEqualTo(DiscountSavingCalculator.calculateSavingOnFinalPrice(percent, bases.get(index), Optional.empty()));
        }
    }

    @Property(seed = "20271028")
    void aPercentageWithCapNeverPassesTheCapNorTheBase(@ForAll @Size(min = 1, max = 10) List<@LongRange(min = 1, max = 99_999_999) Long> baseCents,
                                                       @ForAll @IntRange(min = 1, max = 10000) int percentInHundredths, @ForAll @LongRange(min = 1, max = 2_000_000_000) long capCents) {
        List<NonNegativeMoney> bases = basesOf(baseCents);
        MonetaryAdjustment percent = new MonetaryAdjustment.Proportional(Percentage.ofPoints(BigDecimal.valueOf(percentInHundredths, 2)));

        List<NonNegativeMoney> shares = InvoiceSavingDistributor.distributeInvoiceSavingAcrossLines(percent, capOf(capCents), bases);

        assertThat(sumOf(shares)).isLessThanOrEqualTo(BigDecimal.valueOf(capCents, 2));
        assertThat(sumOf(shares)).isLessThanOrEqualTo(sumOf(bases));
        for (int index = 0; index < shares.size(); index++) {
            assertThat(shares.get(index).value().amount()).isLessThanOrEqualTo(bases.get(index).value().amount());
        }
    }
}
