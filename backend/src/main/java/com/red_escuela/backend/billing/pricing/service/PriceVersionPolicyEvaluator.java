package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.configuration.policy.ConfigurationViolationExceptions;
import com.red_escuela.backend.billing.configuration.policy.EffectiveDateNotRetroactivePolicy;
import com.red_escuela.backend.billing.pricing.exception.CurrencyMismatchException;
import com.red_escuela.backend.billing.pricing.exception.InvalidMarketingDiscountException;
import com.red_escuela.backend.billing.pricing.exception.InvalidPriceAmountException;
import com.red_escuela.backend.billing.pricing.policy.PriceChangeCheck;
import com.red_escuela.backend.billing.pricing.policy.PriceVersionCancellationCheck;
import com.red_escuela.backend.billing.pricing.policy.PriceVersionDraft;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PriceVersionPolicyEvaluator {
    private static final String CURRENCY_MISMATCH = "CURRENCY_MISMATCH";
    private static final String AMOUNT_TOO_LARGE = "PRICE_AMOUNT_TOO_LARGE";

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

    public void requireNotRetroactiveStart(PriceChangeCheck check) {
        PolicyVerdict verdict = new EffectiveDateNotRetroactivePolicy<PriceChangeCheck>().evaluateRule(check);
        if (!verdict.allowed()) {
            throw ConfigurationViolationExceptions.forChangeViolations(List.of(new PolicyViolation(verdict.code(), verdict.reason())));
        }
    }

    public void requireCancellablePriceVersion(PriceVersionCancellationCheck check) {
        priceVersionCancellationPolicySet.evaluateAllRules(check).throwIfDenied(ConfigurationViolationExceptions::forCancellationViolations);
    }

    private static RuntimeException exceptionFor(List<PolicyViolation> violations) {
        if (violations.stream().anyMatch(violation -> CURRENCY_MISMATCH.equals(violation.code()))) {
            return new CurrencyMismatchException(violations);
        }
        if (violations.stream().anyMatch(violation -> AMOUNT_TOO_LARGE.equals(violation.code()))) {
            return new InvalidPriceAmountException(violations);
        }
        return new InvalidMarketingDiscountException(violations);
    }
}
