package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.Set;

@PolicyRule(violationCodes = {"DISCOUNT_TYPE_NOT_IN_CATALOG"}, decisions = {"D-287", "D-240"})
public final class DiscountTypeAvailableInCatalogPolicy implements Policy<DiscountDefinitionCheck> {
    private static final Set<DiscountDefinitionType> NOT_OFFERED = Set.of(DiscountDefinitionType.COHORT_ENTRY, DiscountDefinitionType.PAYMENT_METHOD);

    @Override
    public String ruleName() {
        return "DiscountTypeAvailableInCatalogPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(DiscountDefinitionCheck check) {
        return NOT_OFFERED.contains(check.draft().discountType())
                ? PolicyVerdict.deny(ruleName(), "DISCOUNT_TYPE_NOT_IN_CATALOG", "Este tipo de promoción no se ofrece en el catálogo.")
                : PolicyVerdict.allow(ruleName());
    }
}
