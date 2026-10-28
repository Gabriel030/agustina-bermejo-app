package com.red_escuela.backend.billing.annualupfront.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ENROLLMENT_NOT_ACTIVE"}, decisions = {"D-066", "D-053"})
public final class AnnualUpfrontEnrollmentActivePolicy implements Policy<AnnualUpfrontRequestCheck> {

    @Override
    public String ruleName() {
        return "AnnualUpfrontEnrollmentActivePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AnnualUpfrontRequestCheck check) {
        return !check.enrollmentActive()
                ? PolicyVerdict.deny(ruleName(), "ENROLLMENT_NOT_ACTIVE", "La inscripción no está activa: está suspendida, dada de baja o finalizada.")
                : PolicyVerdict.allow(ruleName());
    }
}
