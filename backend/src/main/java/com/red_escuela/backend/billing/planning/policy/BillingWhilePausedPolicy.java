package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"PAUSED_NOT_BILLED"}, decisions = {"D-050"})
public final class BillingWhilePausedPolicy<C extends ProgramFactsContext> implements InvoicePlanningPolicy<C> {
    public String ruleName() {
        return "BillingWhilePausedPolicy";
    }

    public int plannerQuestionNumber() {
        return 1;
    }

    public PolicyVerdict evaluateRule(C context) {
        return !(context.facts().paused() && !context.terms().paysWhilePaused()) ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "PAUSED_NOT_BILLED", context.notIssuedDetail("inscripción en pausa académica"));
    }
}
