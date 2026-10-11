package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.pricing.exception.CurrencyMismatchException;
import com.red_escuela.backend.billing.pricing.exception.InvalidMarketingDiscountException;
import com.red_escuela.backend.billing.pricing.policy.PriceVersionDraft;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PriceVersionPolicyEvaluator {
    private static final String CURRENCY_MISMATCH = "CURRENCY_MISMATCH";

    private final PolicySet<PriceVersionDraft> priceVersionPolicySet;

    public PriceVersionPolicyEvaluator(PolicySet<PriceVersionDraft> priceVersionPolicySet) {
        this.priceVersionPolicySet = priceVersionPolicySet;
    }

    public void requireValidPriceVersion(PriceVersionDraft draft) {
        priceVersionPolicySet.evaluateAllRules(draft).throwIfDenied(PriceVersionPolicyEvaluator::exceptionFor);
    }

    private static RuntimeException exceptionFor(List<PolicyViolation> violations) {
        return violations.stream().anyMatch(violation -> CURRENCY_MISMATCH.equals(violation.code()))
                ? new CurrencyMismatchException(violations) : new InvalidMarketingDiscountException(violations);
    }
}
