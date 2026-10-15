package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"SCHOLARSHIP_ONLY_FOR_MONTHLY_INSTALLMENTS"}, decisions = {"D-157"})
public final class ScholarshipOnlyForMonthlyInstallmentsPolicy implements Policy<DiscountDefinitionCheck> {

    @Override
    public String ruleName() {
        return "ScholarshipOnlyForMonthlyInstallmentsPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(DiscountDefinitionCheck check) {
        DiscountDefinitionDraft draft = check.draft();
        boolean scholarshipOnAnotherInvoiceType = draft.discountType() == DiscountDefinitionType.SCHOLARSHIP
                && draft.applicableInvoiceType() != null && draft.applicableInvoiceType() != InvoiceType.MONTHLY_INSTALLMENT;
        return scholarshipOnAnotherInvoiceType
                ? PolicyVerdict.deny(ruleName(), "SCHOLARSHIP_ONLY_FOR_MONTHLY_INSTALLMENTS", "La beca solo aplica a cuotas mensuales. Para bonificar la matrícula, creá una promoción de tipo matrícula.")
                : PolicyVerdict.allow(ruleName());
    }
}
