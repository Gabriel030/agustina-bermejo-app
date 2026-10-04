package com.red_escuela.backend.billing.issuing.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

public final class PromotionBenefitCapPolicy implements Policy<PromotionBenefitCapPolicy.Usage> {
    public record Usage(Integer scopeCount, long alreadyApplied) {
    }

    public String name() {
        return "PromotionBenefitCapPolicy";
    }

    public PolicyVerdict evaluate(Usage usage) {
        return usage.scopeCount() == null || usage.alreadyApplied() < usage.scopeCount()
                ? PolicyVerdict.allow(name())
                : PolicyVerdict.deny(name(), "PROMOTION_BENEFIT_EXHAUSTED", "El beneficio de la promoción ya se aplicó el máximo de veces");
    }
}
