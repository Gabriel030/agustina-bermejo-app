package com.red_escuela.backend.billing.run.policy;

import com.red_escuela.backend.billing.run.domain.BillingRunStatus;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "PREVIOUS_STEP_FAILED", decisions = {"D-356", "D-449"})
public final class PreviousStepAllowsNextPolicy implements Policy<PreviousStepContext> {

    @Override
    public String ruleName() {
        return "PreviousStepAllowsNextPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(PreviousStepContext context) {
        return context.previousStepStatus() == BillingRunStatus.FAILED
                ? PolicyVerdict.deny(ruleName(), "PREVIOUS_STEP_FAILED", "El paso anterior falló entero: los siguientes no corren")
                : PolicyVerdict.allow(ruleName());
    }
}
