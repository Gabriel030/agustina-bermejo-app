package com.red_escuela.backend.billing.pricing;

import com.red_escuela.backend.billing.configuration.policy.EffectiveDateNotRetroactivePolicy;
import com.red_escuela.backend.billing.pricing.policy.PriceCancellationReasonPolicy;
import com.red_escuela.backend.billing.pricing.policy.PriceChangeCheck;
import com.red_escuela.backend.billing.pricing.policy.PriceChangeReasonPolicy;
import com.red_escuela.backend.billing.pricing.policy.PriceVersionCancellationCheck;
import com.red_escuela.backend.billing.pricing.policy.ScheduledPriceVersionCancellablePolicy;
import com.red_escuela.backend.billing.pricing.policy.MarketingDiscountFixedNotAboveBasePolicy;
import com.red_escuela.backend.billing.pricing.policy.MarketingDiscountNotNegativePolicy;
import com.red_escuela.backend.billing.pricing.policy.PriceCurrencyPolicy;
import com.red_escuela.backend.billing.pricing.policy.PriceVersionDraft;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PricingPolicySetsConfiguration {

    @Bean
    public PolicySet<PriceVersionDraft> priceVersionPolicySet() {
        List<Policy<PriceVersionDraft>> ordered = List.of(
                new PriceCurrencyPolicy(),
                new MarketingDiscountNotNegativePolicy(),
                new MarketingDiscountFixedNotAboveBasePolicy());
        return PolicySet.named("PRICE_VERSION", ordered);
    }

    @Bean
    public PolicySet<PriceChangeCheck> priceChangePolicySet() {
        List<Policy<PriceChangeCheck>> ordered = List.of(
                new PriceChangeReasonPolicy(),
                new EffectiveDateNotRetroactivePolicy<>());
        return PolicySet.named("PRICE_CHANGE", ordered);
    }

    @Bean
    public PolicySet<PriceVersionCancellationCheck> priceVersionCancellationPolicySet() {
        List<Policy<PriceVersionCancellationCheck>> ordered = List.of(
                new PriceCancellationReasonPolicy(),
                new ScheduledPriceVersionCancellablePolicy());
        return PolicySet.named("PRICE_VERSION_CANCELLATION", ordered);
    }
}
