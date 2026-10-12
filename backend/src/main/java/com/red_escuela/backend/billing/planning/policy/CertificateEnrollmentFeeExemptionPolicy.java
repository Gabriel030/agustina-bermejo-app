package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.billing.planning.domain.EnrollmentFeeRequirementScope;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"NOT_REQUIRED_FOR_INACTIVE_OR_COMPLETED"}, decisions = {"D-015"})
public final class CertificateEnrollmentFeeExemptionPolicy implements Policy<EnrollmentFeeRequirementContext> {
    public String ruleName() {
        return "CertificateEnrollmentFeeExemptionPolicy";
    }

    public PolicyVerdict evaluateRule(EnrollmentFeeRequirementContext context) {
        return !(context.scope() == EnrollmentFeeRequirementScope.CERTIFICATE && (!context.facts().academicallyActive() || context.facts().planCompleted())) ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "NOT_REQUIRED_FOR_INACTIVE_OR_COMPLETED", "el certificado no exige matrícula a egresados ni inscripciones inactivas");
    }
}
