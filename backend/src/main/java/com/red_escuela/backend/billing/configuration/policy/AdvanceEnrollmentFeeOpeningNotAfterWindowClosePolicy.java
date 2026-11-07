package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ADVANCE_FEE_OPENING_AFTER_WINDOW_CLOSE", decisions = {"D-528"})
public final class AdvanceEnrollmentFeeOpeningNotAfterWindowClosePolicy implements Policy<YearBillingConfigurationChange> {
    public String ruleName() {
        return "AdvanceEnrollmentFeeOpeningNotAfterWindowClosePolicy";
    }

    public PolicyVerdict evaluateRule(YearBillingConfigurationChange change) {
        boolean opensAfterClose = change.proposed().advanceEnrollmentFeeOpensOn() != null
                && change.enrollmentWindowClosesOn().map(closesOn -> change.proposed().advanceEnrollmentFeeOpensOn().isAfter(closesOn)).orElse(false);
        return opensAfterClose ? PolicyVerdict.deny(ruleName(), "ADVANCE_FEE_OPENING_AFTER_WINDOW_CLOSE",
                "La apertura de la matrícula anticipada no puede ser posterior al cierre de la ventana de inscripción.")
                : PolicyVerdict.allow(ruleName());
    }
}
