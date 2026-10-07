package com.red_escuela.backend.billing.invoice.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "PROMOTION_BENEFIT_EXHAUSTED", decisions = {"D-074"})
public final class PromotionBenefitCapPolicy implements Policy<PromotionBenefitCapPolicy.Usage> {
    public record Usage(Integer scopeCount, long alreadyApplied) {
    }

    public String ruleName() {
        return "PromotionBenefitCapPolicy";
    }

    public PolicyVerdict evaluateRule(Usage usage) {
        return usage.scopeCount() == null || usage.alreadyApplied() < usage.scopeCount()
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "PROMOTION_BENEFIT_EXHAUSTED", "El beneficio de la promoción ya se aplicó el máximo de veces");
    }
}
