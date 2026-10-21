package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ENROLLMENT_FEE_ALREADY_ISSUED"}, decisions = {"D-402"})
public final class EnrollmentFeeDiscountBeforeIssuePolicy implements Policy<StudentDiscountGrantCheck> {

    @Override
    public String ruleName() {
        return "EnrollmentFeeDiscountBeforeIssuePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(StudentDiscountGrantCheck check) {
        return check.enrollmentFeeAlreadyIssued()
                ? PolicyVerdict.deny(ruleName(), "ENROLLMENT_FEE_ALREADY_ISSUED",
                        "La matrícula de ese año lectivo ya está emitida: un descuento sobre la matrícula solo se otorga antes de emitirla.")
                : PolicyVerdict.allow(ruleName());
    }
}
