package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "EFFECTIVE_FROM_BEFORE_NEXT_DAY", decisions = {"D-350"})
public final class CollectionReminderEffectiveFromNextDayPolicy implements Policy<CollectionReminderEffectiveFromCheck> {
    public String ruleName() {
        return "CollectionReminderEffectiveFromNextDayPolicy";
    }

    public PolicyVerdict evaluateRule(CollectionReminderEffectiveFromCheck check) {
        return check.effectiveFrom().isAfter(check.today())
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "EFFECTIVE_FROM_BEFORE_NEXT_DAY", "La vigencia debe ser como mínimo el día siguiente.");
    }
}
