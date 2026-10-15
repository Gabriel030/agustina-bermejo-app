package com.red_escuela.backend.billing.invoice.policy.issuing;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "PROMOTION_DISCOUNT_EXHAUSTED", decisions = {"D-074"})
public final class PromotionDiscountInstallmentCapPolicy implements Policy<PromotionDiscountInstallmentCapPolicy.Usage> {

    public record Usage(Integer scopeCount, long alreadyApplied) {
    }

    @Override
    public String ruleName() {
        return "PromotionDiscountInstallmentCapPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(Usage usage) {
        return usage.scopeCount() == null || usage.alreadyApplied() < usage.scopeCount()
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "PROMOTION_DISCOUNT_EXHAUSTED", "El beneficio de la promoción ya se aplicó el máximo de veces");
    }
}
