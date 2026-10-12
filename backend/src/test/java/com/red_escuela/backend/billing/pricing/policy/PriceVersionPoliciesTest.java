package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.billing.pricing.domain.MarketingDiscountType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.InvalidPercentageException;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.billing.pricing.PricingPolicySetsConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-240")
@Tag("D-097")
class PriceVersionPoliciesTest {
    private static final Money BASE = ars("100000.00");
    private final PolicySet<PriceVersionDraft> policies = new PricingPolicySetsConfiguration().priceVersionPolicySet();

    private static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    private List<String> violationCodesOf(PriceVersionDraft draft) {
        return policies.evaluateAllRules(draft).violations().stream().map(violation -> violation.code()).toList();
    }

    @Test
    void aPriceWithoutDiscountInTheCurrencyOfTheOfferingIsValid() {
        assertThat(violationCodesOf(PriceVersionDraft.withoutDiscount(Currency.ARS, BASE))).isEmpty();
    }

    @Test
    void aPercentageDiscountAbove100CannotEvenBeBuilt() {
        assertThatThrownBy(() -> Percentage.ofPoints(new BigDecimal("120"))).isInstanceOf(InvalidPercentageException.class);
    }

    @Test
    void aFixedDiscountAboveTheBaseIsRejected() {
        assertThat(violationCodesOf(PriceVersionDraft.withFixedDiscount(Currency.ARS, BASE, ars("150000.00")))).containsExactly("MARKETING_DISCOUNT_EXCEEDS_BASE");
    }

    @Test
    void aFixedDiscountInAnotherCurrencyIsRejected() {
        assertThat(violationCodesOf(PriceVersionDraft.withFixedDiscount(Currency.ARS, BASE, Money.of(new BigDecimal("10.00"), Currency.USD))))
                .containsExactly("CURRENCY_MISMATCH");
    }

    @Test
    void aBasePriceInAnotherCurrencyThanTheOfferingIsRejected() {
        assertThat(violationCodesOf(PriceVersionDraft.withoutDiscount(Currency.USD, BASE))).containsExactly("CURRENCY_MISMATCH");
    }

    @Test
    void aNegativeFixedDiscountIsRejected() {
        assertThat(violationCodesOf(PriceVersionDraft.withFixedDiscount(Currency.ARS, BASE, ars("-0.01")))).containsExactly("NEGATIVE_MARKETING_DISCOUNT");
    }

    @Test
    void theLimitsAreAccepted() {
        assertThat(violationCodesOf(PriceVersionDraft.withPercentageDiscount(Currency.ARS, BASE, Percentage.ofPoints(new BigDecimal("100"))))).isEmpty();
        assertThat(violationCodesOf(PriceVersionDraft.withPercentageDiscount(Currency.ARS, BASE, Percentage.ofPoints(BigDecimal.ZERO)))).isEmpty();
        assertThat(violationCodesOf(PriceVersionDraft.withFixedDiscount(Currency.ARS, BASE, BASE))).isEmpty();
        assertThat(violationCodesOf(PriceVersionDraft.withFixedDiscount(Currency.ARS, BASE, ars("0.00")))).isEmpty();
    }

    @Test
    void aDiscountNeedsItsTypeAndExactlyItsOwnValue() {
        assertThatThrownBy(() -> new PriceVersionDraft(Currency.ARS, BASE, MarketingDiscountType.FIXED, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PriceVersionDraft(Currency.ARS, BASE, null, Percentage.ofPoints(BigDecimal.TEN), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PriceVersionDraft(Currency.ARS, BASE, MarketingDiscountType.PERCENTAGE, Percentage.ofPoints(BigDecimal.TEN), BASE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
