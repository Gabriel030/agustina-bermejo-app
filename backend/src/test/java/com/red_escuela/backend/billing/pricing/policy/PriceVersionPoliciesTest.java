package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.billing.pricing.PricingPolicySetsConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-240")
@Tag("D-097")
class PriceVersionPoliciesTest {
    private static final BigDecimal BASE = new BigDecimal("100000.00");
    private final PolicySet<PriceVersionDraft> policies = new PricingPolicySetsConfiguration().priceVersionPolicySet();

    private List<String> violationCodesOf(PriceVersionDraft draft) {
        return policies.evaluateAllRules(draft).violations().stream().map(violation -> violation.code()).toList();
    }

    @Test
    void aPriceWithoutDiscountInTheCurrencyOfTheOfferingIsValid() {
        assertThat(violationCodesOf(PriceVersionDraft.withoutDiscount(Currency.ARS, Currency.ARS, BASE))).isEmpty();
    }

    @Test
    void aPercentageDiscountAbove100IsRejected() {
        assertThat(violationCodesOf(PriceVersionDraft.withPercentageDiscount(Currency.ARS, Currency.ARS, BASE, new BigDecimal("120"))))
                .containsExactly("MARKETING_DISCOUNT_PERCENTAGE_ABOVE_HUNDRED");
    }

    @Test
    void aFixedDiscountAboveTheBaseIsRejected() {
        assertThat(violationCodesOf(PriceVersionDraft.withFixedDiscount(Currency.ARS, Currency.ARS, BASE, new BigDecimal("150000.00"), Currency.ARS)))
                .containsExactly("MARKETING_DISCOUNT_EXCEEDS_BASE");
    }

    @Test
    void aFixedDiscountInAnotherCurrencyIsRejected() {
        assertThat(violationCodesOf(PriceVersionDraft.withFixedDiscount(Currency.ARS, Currency.ARS, BASE, new BigDecimal("10.00"), Currency.USD)))
                .containsExactly("CURRENCY_MISMATCH");
    }

    @Test
    void aBasePriceInAnotherCurrencyThanTheOfferingIsRejected() {
        assertThat(violationCodesOf(PriceVersionDraft.withoutDiscount(Currency.USD, Currency.ARS, BASE))).containsExactly("CURRENCY_MISMATCH");
    }

    @Test
    void negativeDiscountsAreRejectedWhateverTheirType() {
        assertThat(violationCodesOf(PriceVersionDraft.withPercentageDiscount(Currency.ARS, Currency.ARS, BASE, new BigDecimal("-1"))))
                .containsExactly("NEGATIVE_MARKETING_DISCOUNT");
        assertThat(violationCodesOf(PriceVersionDraft.withFixedDiscount(Currency.ARS, Currency.ARS, BASE, new BigDecimal("-0.01"), Currency.ARS)))
                .containsExactly("NEGATIVE_MARKETING_DISCOUNT");
    }

    @Test
    void theLimitsAreAccepted() {
        assertThat(violationCodesOf(PriceVersionDraft.withPercentageDiscount(Currency.ARS, Currency.ARS, BASE, new BigDecimal("100")))).isEmpty();
        assertThat(violationCodesOf(PriceVersionDraft.withPercentageDiscount(Currency.ARS, Currency.ARS, BASE, BigDecimal.ZERO))).isEmpty();
        assertThat(violationCodesOf(PriceVersionDraft.withFixedDiscount(Currency.ARS, Currency.ARS, BASE, BASE, Currency.ARS))).isEmpty();
        assertThat(violationCodesOf(PriceVersionDraft.withFixedDiscount(Currency.ARS, Currency.ARS, BASE, BigDecimal.ZERO, Currency.ARS))).isEmpty();
    }

    @Test
    void aDiscountNeedsBothItsTypeAndItsValue() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new PriceVersionDraft(Currency.ARS, Currency.ARS, BASE,
                com.red_escuela.backend.billing.pricing.domain.MarketingDiscountType.FIXED, null, Currency.ARS)).isInstanceOf(IllegalArgumentException.class);
    }
}
