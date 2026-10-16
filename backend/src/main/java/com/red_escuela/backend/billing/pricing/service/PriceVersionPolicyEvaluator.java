package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.configuration.policy.ConfigurationViolationExceptions;
import com.red_escuela.backend.billing.pricing.exception.CurrencyMismatchException;
import com.red_escuela.backend.billing.pricing.exception.InvalidMarketingDiscountException;
import com.red_escuela.backend.billing.pricing.policy.PriceChangeCheck;
import com.red_escuela.backend.billing.pricing.policy.PriceVersionCancellationCheck;
import com.red_escuela.backend.billing.pricing.policy.PriceVersionDraft;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PriceVersionPolicyEvaluator {
    private static final String CURRENCY_MISMATCH = "CURRENCY_MISMATCH";

    private final PolicySet<PriceVersionDraft> priceVersionPolicySet;
    private final PolicySet<PriceChangeCheck> priceChangePolicySet;
    private final PolicySet<PriceVersionCancellationCheck> priceVersionCancellationPolicySet;

    public PriceVersionPolicyEvaluator(PolicySet<PriceVersionDraft> priceVersionPolicySet, PolicySet<PriceChangeCheck> priceChangePolicySet,
                                       PolicySet<PriceVersionCancellationCheck> priceVersionCancellationPolicySet) {
        this.priceVersionPolicySet = priceVersionPolicySet;
        this.priceChangePolicySet = priceChangePolicySet;
        this.priceVersionCancellationPolicySet = priceVersionCancellationPolicySet;
    }

    public void requireValidPriceVersion(PriceVersionDraft draft) {
        priceVersionPolicySet.evaluateAllRules(draft).throwIfDenied(PriceVersionPolicyEvaluator::exceptionFor);
    }

    public void requireValidPriceChange(PriceChangeCheck check) {
        priceChangePolicySet.evaluateAllRules(check).throwIfDenied(ConfigurationViolationExceptions::forChangeViolations);
    }

    public void requireCancellablePriceVersion(PriceVersionCancellationCheck check) {
        priceVersionCancellationPolicySet.evaluateAllRules(check).throwIfDenied(ConfigurationViolationExceptions::forCancellationViolations);
    }

    private static RuntimeException exceptionFor(List<PolicyViolation> violations) {
        return violations.stream().anyMatch(violation -> CURRENCY_MISMATCH.equals(violation.code()))
                ? new CurrencyMismatchException(violations) : new InvalidMarketingDiscountException(violations);
    }
}
