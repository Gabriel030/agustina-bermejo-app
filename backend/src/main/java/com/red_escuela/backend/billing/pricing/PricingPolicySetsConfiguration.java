package com.red_escuela.backend.billing.pricing;

import com.red_escuela.backend.billing.configuration.policy.EffectiveDateNotRetroactivePolicy;
import com.red_escuela.backend.billing.configuration.policy.ProposalApprovalCheck;
import com.red_escuela.backend.billing.configuration.policy.ProposalNotExpiredPolicy;
import com.red_escuela.backend.billing.pricing.policy.AdvancePriceCheck;
import com.red_escuela.backend.billing.pricing.policy.AdvancePriceStartsBeforeRegularEmissionPolicy;
import com.red_escuela.backend.billing.pricing.policy.AdvancePriceStartsInTheFuturePolicy;
import com.red_escuela.backend.billing.pricing.policy.LectiveYearOnlyForEnrollmentFeePolicy;
import com.red_escuela.backend.billing.pricing.policy.PriceAmountWithinLimitPolicy;
import com.red_escuela.backend.billing.pricing.policy.PriceCancellationReasonPolicy;
import com.red_escuela.backend.billing.pricing.policy.PriceChangeCheck;
import com.red_escuela.backend.billing.pricing.policy.PriceChangeReasonPolicy;
import com.red_escuela.backend.billing.pricing.policy.PriceProposalSlot;
import com.red_escuela.backend.billing.pricing.policy.PriceRejectionCheck;
import com.red_escuela.backend.billing.pricing.policy.PriceRejectionReasonPolicy;
import com.red_escuela.backend.billing.pricing.policy.PriceVersionCancellationCheck;
import com.red_escuela.backend.billing.pricing.policy.SingleOpenPriceProposalPolicy;
import com.red_escuela.backend.billing.pricing.policy.ScheduledPriceVersionCancellablePolicy;
import com.red_escuela.backend.billing.pricing.policy.MarketingDiscountFixedNotAboveBasePolicy;
import com.red_escuela.backend.billing.pricing.policy.MarketingDiscountLeavesAPricePolicy;
import com.red_escuela.backend.billing.pricing.policy.MarketingDiscountNotNegativePolicy;
import com.red_escuela.backend.billing.pricing.policy.OfferingListPricePositivePolicy;
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
                new PriceAmountWithinLimitPolicy(),
                new OfferingListPricePositivePolicy(),
                new MarketingDiscountNotNegativePolicy(),
                new MarketingDiscountFixedNotAboveBasePolicy(),
                new MarketingDiscountLeavesAPricePolicy());
        return PolicySet.named("PRICE_VERSION", ordered);
    }

    @Bean
    public PolicySet<AdvancePriceCheck> advancePricePolicySet() {
        List<Policy<AdvancePriceCheck>> ordered = List.of(
                new LectiveYearOnlyForEnrollmentFeePolicy(),
                new AdvancePriceStartsInTheFuturePolicy(),
                new AdvancePriceStartsBeforeRegularEmissionPolicy());
        return PolicySet.named("ADVANCE_PRICE", ordered);
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

    @Bean
    public PolicySet<PriceProposalSlot> priceProposalPolicySet() {
        List<Policy<PriceProposalSlot>> ordered = List.of(new SingleOpenPriceProposalPolicy());
        return PolicySet.named("PRICE_PROPOSAL", ordered);
    }

    @Bean
    public PolicySet<ProposalApprovalCheck> priceProposalApprovalPolicySet() {
        List<Policy<ProposalApprovalCheck>> ordered = List.of(new ProposalNotExpiredPolicy());
        return PolicySet.named("PRICE_PROPOSAL_APPROVAL", ordered);
    }

    @Bean
    public PolicySet<PriceRejectionCheck> priceProposalRejectionPolicySet() {
        List<Policy<PriceRejectionCheck>> ordered = List.of(new PriceRejectionReasonPolicy());
        return PolicySet.named("PRICE_PROPOSAL_REJECTION", ordered);
    }
}
