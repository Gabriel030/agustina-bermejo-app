package com.red_escuela.backend.billing.account.policy;

import com.red_escuela.backend.billing.account.domain.OpenInvoiceView;
import com.red_escuela.backend.billing.account.domain.PlannedInvoiceAllocation;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "PAYMENT_ALLOCATION_ACCOUNT_MISMATCH", decisions = {"D-097"})
public final class PaymentAllocationSameAccountPolicy implements Policy<AllocationPlanContext> {

    @Override
    public String ruleName() {
        return "PaymentAllocationSameAccountPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AllocationPlanContext context) {
        for (PlannedInvoiceAllocation planned : context.plan()) {
            boolean belongsToTheAccount = context.findOpenInvoice(planned.invoiceId())
                    .filter(invoice -> isOfTheAccountAndCurrency(invoice, context))
                    .filter(invoice -> planned.amount().value().currency() == context.currency())
                    .isPresent();
            if (!belongsToTheAccount) {
                return PolicyVerdict.deny(ruleName(), "PAYMENT_ALLOCATION_ACCOUNT_MISMATCH",
                        "La factura " + planned.invoiceId() + " no pertenece a la cuenta corriente o está en otra moneda");
            }
        }
        return PolicyVerdict.allow(ruleName());
    }

    private boolean isOfTheAccountAndCurrency(OpenInvoiceView invoice, AllocationPlanContext context) {
        return invoice.studentCurrentAccountId().equals(context.studentCurrentAccountId()) && invoice.currency() == context.currency();
    }
}
