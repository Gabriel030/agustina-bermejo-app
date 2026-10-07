package com.red_escuela.backend.billing.invoice.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingContext;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "DUE_DATE_BEFORE_ISSUE_DATE", decisions = {"D-057"})
public final class DueDateNotBeforeIssueDatePolicy implements Policy<InvoiceIssuingContext> {
    public String ruleName() {
        return "DueDateNotBeforeIssueDatePolicy";
    }

    public PolicyVerdict evaluateRule(InvoiceIssuingContext ctx) {
        return !ctx.request().dueDate().isBefore(ctx.today()) ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "DUE_DATE_BEFORE_ISSUE_DATE", "El vencimiento no puede ser anterior a la fecha de emisión");
    }
}
