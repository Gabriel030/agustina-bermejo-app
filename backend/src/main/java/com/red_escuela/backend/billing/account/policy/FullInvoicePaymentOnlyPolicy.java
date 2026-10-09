package com.red_escuela.backend.billing.account.policy;

import com.red_escuela.backend.billing.account.domain.PlannedInvoiceAllocation;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "PARTIAL_PAYMENT_NOT_ALLOWED", decisions = {"D-136"})
public final class FullInvoicePaymentOnlyPolicy implements Policy<AllocationPlanContext> {

    @Override
    public String ruleName() {
        return "FullInvoicePaymentOnlyPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AllocationPlanContext context) {
        for (PlannedInvoiceAllocation planned : context.plan()) {
            boolean coversTheWholeOutstanding = context.findOpenInvoice(planned.invoiceId())
                    .filter(invoice -> invoice.outstandingAmount().equals(planned.amount()))
                    .isPresent();
            if (!coversTheWholeOutstanding) {
                return PolicyVerdict.deny(ruleName(), "PARTIAL_PAYMENT_NOT_ALLOWED",
                        "La factura " + planned.invoiceId() + " solo se paga completa");
            }
        }
        return PolicyVerdict.allow(ruleName());
    }
}
