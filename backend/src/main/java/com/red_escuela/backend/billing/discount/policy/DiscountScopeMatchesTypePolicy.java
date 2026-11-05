package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"INVALID_DISCOUNT_DEFINITION"}, decisions = {"D-240"})
public final class DiscountScopeMatchesTypePolicy implements Policy<DiscountDefinitionCheck> {
    private static final String CODE = "INVALID_DISCOUNT_DEFINITION";

    @Override
    public String ruleName() {
        return "DiscountScopeMatchesTypePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(DiscountDefinitionCheck check) {
        DiscountDefinitionDraft draft = check.draft();
        if (draft.discountType() == DiscountDefinitionType.COHORT_PROMOTION) {
            return evaluateCohortPromotionScope(check);
        }
        if (draft.discountType() == DiscountDefinitionType.ANNUAL_UPFRONT) {
            return draft.courseId() != null || draft.programCohortId() != null
                    ? PolicyVerdict.deny(ruleName(), CODE, "La promoción de pago anual puede acotarse a una carrera, a una ventana y a un cupo, no a un curso ni a una cohorte.")
                    : PolicyVerdict.allow(ruleName());
        }
        boolean carriesScope = draft.hasOfferingScope() || draft.programCohortId() != null || draft.enrollmentWindowFrom() != null
                || draft.enrollmentWindowTo() != null || draft.quota() != null;
        return carriesScope ? PolicyVerdict.deny(ruleName(), CODE, "El alcance, la ventana de inscripción y el cupo solo aplican a las promociones de cohorte o marketing.")
                : PolicyVerdict.allow(ruleName());
    }

    private PolicyVerdict evaluateCohortPromotionScope(DiscountDefinitionCheck check) {
        DiscountDefinitionDraft draft = check.draft();
        if (draft.programId() != null == (draft.courseId() != null)) {
            return PolicyVerdict.deny(ruleName(), CODE, "La promoción de cohorte o marketing aplica a una carrera o a un curso, no a ambos ni a ninguno.");
        }
        if (draft.programCohortId() != null && draft.programId() == null) {
            return PolicyVerdict.deny(ruleName(), CODE, "La cohorte solo se acota dentro de una carrera.");
        }
        if (check.findCohortProgramId().map(cohortProgramId -> !cohortProgramId.equals(draft.programId())).orElse(false)) {
            return PolicyVerdict.deny(ruleName(), CODE, "La cohorte no pertenece a la carrera indicada.");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
