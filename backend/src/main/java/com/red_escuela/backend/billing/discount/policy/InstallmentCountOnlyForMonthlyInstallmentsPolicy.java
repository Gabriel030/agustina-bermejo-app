package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"INSTALLMENT_COUNT_ONLY_FOR_MONTHLY_INSTALLMENTS"}, decisions = {"D-075"})
public final class InstallmentCountOnlyForMonthlyInstallmentsPolicy implements Policy<DiscountDefinitionCheck> {

    @Override
    public String ruleName() {
        return "InstallmentCountOnlyForMonthlyInstallmentsPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(DiscountDefinitionCheck check) {
        DiscountDefinitionDraft draft = check.draft();
        boolean countOnAnotherInvoiceType = draft.installmentsTotal() != null && draft.applicableInvoiceType() != null
                && draft.applicableInvoiceType() != InvoiceType.MONTHLY_INSTALLMENT;
        return countOnAnotherInvoiceType
                ? PolicyVerdict.deny(ruleName(), "INSTALLMENT_COUNT_ONLY_FOR_MONTHLY_INSTALLMENTS", "La cantidad de cuotas solo aplica a promociones de cuota mensual.")
                : PolicyVerdict.allow(ruleName());
    }
}
