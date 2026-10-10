package com.red_escuela.backend.billing.pricing.domain;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-240")
class MarketingDiscountTest {
    private static final LocalDate DAY = LocalDate.of(2027, 3, 1);
    private static final Instant NOW = Instant.parse("2027-03-01T12:00:00Z");

    private static NonNegativeMoney ars(String amount) {
        return NonNegativeMoney.of(new BigDecimal(amount), Currency.ARS);
    }

    private static PriceVersion versionWith(MarketingDiscount discount) {
        return Price.forProgram(1L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual")
                .publishPriceVersion(ars("100000.00"), discount, DAY, "ad", 9L, false, DAY, NOW);
    }

    @Test
    void aHalfPriceCampaignLeavesHalfOfTheListPrice() {
        PriceVersion version = versionWith(MarketingDiscount.ofPercentage(Percentage.ofPoints(new BigDecimal("50"))));

        assertThat(version.finalAmount()).isEqualTo(ars("50000.00"));
        assertThat(version.marketingDiscountAmount()).isEqualTo(ars("50000.00"));
    }

    @Test
    void aFixedCampaignSubtractsTheAmountFromTheListPrice() {
        PriceVersion version = versionWith(MarketingDiscount.ofFixedAmount(ars("30000.00")));

        assertThat(version.finalAmount()).isEqualTo(ars("70000.00"));
        assertThat(version.marketingDiscountAmount()).isEqualTo(ars("30000.00"));
    }

    @Test
    void withoutACampaignTheFinalPriceIsTheListPrice() {
        PriceVersion version = versionWith(null);

        assertThat(version.finalAmount()).isEqualTo(ars("100000.00"));
        assertThat(version.marketingDiscountAmount()).isEqualTo(ars("0.00"));
        assertThat(version.marketingDiscount()).isEmpty();
    }

    @Test
    void thePercentageRoundsHalfUpToTwoDecimals() {
        Price price = Price.forProgram(1L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual");
        PriceVersion version = price.publishPriceVersion(ars("100.05"), MarketingDiscount.ofPercentage(Percentage.ofPoints(new BigDecimal("12.5"))), DAY, "ad", 9L, false, DAY, NOW);

        assertThat(version.marketingDiscountAmount()).isEqualTo(ars("12.51"));
        assertThat(version.finalAmount()).isEqualTo(ars("87.54"));
    }

    @Test
    void aFullDiscountOrAFixedDiscountEqualToTheBaseLeavesAFinalPriceOfZero() {
        assertThat(versionWith(MarketingDiscount.ofPercentage(Percentage.ofPoints(new BigDecimal("100")))).finalAmount()).isEqualTo(ars("0.00"));
        assertThat(versionWith(MarketingDiscount.ofFixedAmount(ars("100000.00"))).finalAmount()).isEqualTo(ars("0.00"));
    }

    @Test
    void aFixedDiscountKeepsItsCurrency() {
        NonNegativeMoney thirtyThousandDollars = NonNegativeMoney.of(new BigDecimal("30000.00"), Currency.USD);

        assertThat(MarketingDiscount.ofFixedAmount(thirtyThousandDollars).fixedAmount()).isEqualTo(thirtyThousandDollars);
        assertThat(MarketingDiscount.ofFixedAmount(thirtyThousandDollars).percentage()).isNull();
    }

    @Test
    void anArsPriceRefusesToPublishAFixedDiscountInDollars() {
        Price arsPrice = Price.forProgram(1L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual");
        MarketingDiscount dollars = MarketingDiscount.ofFixedAmount(NonNegativeMoney.of(new BigDecimal("30000.00"), Currency.USD));

        assertThatThrownBy(() -> arsPrice.publishPriceVersion(ars("100000.00"), dollars, DAY, "ad", 9L, false, DAY, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("discount in USD");
        assertThat(arsPrice.getVersions()).isEmpty();
    }

    @Test
    void anArsPriceRefusesToProposeAFixedDiscountInDollars() {
        Price arsPrice = Price.forProgram(1L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual");
        MarketingDiscount dollars = MarketingDiscount.ofFixedAmount(NonNegativeMoney.of(new BigDecimal("30000.00"), Currency.USD));

        assertThatThrownBy(() -> arsPrice.proposePriceVersion(ars("100000.00"), dollars, DAY, "ad", 9L, 5L, false, DAY, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("discount in USD");
        assertThat(arsPrice.getVersions()).isEmpty();
    }

    @Test
    void aPriceRefusesAFixedDiscountAboveItsBaseWhenPublishedOrProposed() {
        Price price = Price.forProgram(1L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual");
        MarketingDiscount above = MarketingDiscount.ofFixedAmount(ars("100000.01"));

        assertThatThrownBy(() -> price.publishPriceVersion(ars("100000.00"), above, DAY, "ad", 9L, false, DAY, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> price.proposePriceVersion(ars("100000.00"), above, DAY, "ad", 9L, 5L, false, DAY, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThat(price.getVersions()).isEmpty();
    }

    @Test
    void aDiscountCarriesExactlyItsOwnValue() {
        assertThatThrownBy(() -> new MarketingDiscount(MarketingDiscountType.FIXED, Percentage.ofPoints(BigDecimal.TEN), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MarketingDiscount(MarketingDiscountType.PERCENTAGE, Percentage.ofPoints(BigDecimal.TEN), ars("1.00"))).isInstanceOf(IllegalArgumentException.class);
    }
}
