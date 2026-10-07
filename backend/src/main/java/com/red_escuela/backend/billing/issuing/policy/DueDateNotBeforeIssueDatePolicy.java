package com.red_escuela.backend.billing.issuing.policy;

import com.red_escuela.backend.billing.issuing.IssuanceContext;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "DUE_DATE_BEFORE_ISSUE_DATE", decisions = {"D-057"})
public final class DueDateNotBeforeIssueDatePolicy implements Policy<IssuanceContext> {
    public String name() {
        return "DueDateNotBeforeIssueDatePolicy";
    }

    public PolicyVerdict evaluate(IssuanceContext ctx) {
        return !ctx.request().dueDate().isBefore(ctx.today()) ? PolicyVerdict.allow(name()) : PolicyVerdict.deny(name(), "DUE_DATE_BEFORE_ISSUE_DATE", "El vencimiento no puede ser anterior a la fecha de emisión");
    }
}
