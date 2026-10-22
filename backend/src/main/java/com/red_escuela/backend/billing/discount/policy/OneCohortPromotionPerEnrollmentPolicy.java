package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"COHORT_PROMOTION_ALREADY_ACQUIRED"}, decisions = {"D-386"})
public final class OneCohortPromotionPerEnrollmentPolicy implements Policy<CohortPromotionAcquisitionCheck> {

    @Override
    public String ruleName() {
        return "OneCohortPromotionPerEnrollmentPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CohortPromotionAcquisitionCheck check) {
        return check.alreadyAcquiredStudentDiscountId() == null
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "COHORT_PROMOTION_ALREADY_ACQUIRED",
                        "La inscripción ya tiene una promoción de cohorte (n.º " + check.alreadyAcquiredStudentDiscountId() + ") y no puede tener otra.");
    }
}
