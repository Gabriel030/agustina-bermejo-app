package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"INVALID_DISCOUNT_DEFINITION"}, decisions = {"D-283"})
public final class DiscountAvailabilityPeriodPolicy implements Policy<DiscountDefinitionCheck> {
    private static final String CODE = "INVALID_DISCOUNT_DEFINITION";

    @Override
    public String ruleName() {
        return "DiscountAvailabilityPeriodPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(DiscountDefinitionCheck check) {
        DiscountDefinitionDraft draft = check.draft();
        if (draft.availableFrom() == null) {
            return PolicyVerdict.deny(ruleName(), CODE, "La fecha de disponibilidad desde es obligatoria.");
        }
        if (draft.availableUntil() != null && draft.availableUntil().isBefore(draft.availableFrom())) {
            return PolicyVerdict.deny(ruleName(), CODE, "La disponibilidad hasta no puede ser anterior a la disponibilidad desde.");
        }
        if (draft.enrollmentWindowFrom() != null && draft.enrollmentWindowTo() != null && draft.enrollmentWindowTo().isBefore(draft.enrollmentWindowFrom())) {
            return PolicyVerdict.deny(ruleName(), CODE, "La ventana de inscripción termina antes de empezar.");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
