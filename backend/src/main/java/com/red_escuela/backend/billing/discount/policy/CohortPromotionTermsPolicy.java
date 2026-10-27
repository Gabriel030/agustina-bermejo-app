package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.Set;

@PolicyRule(violationCodes = {"INVALID_DISCOUNT_DEFINITION"}, decisions = {"D-432", "D-326"})
public final class CohortPromotionTermsPolicy implements Policy<DiscountDefinitionCheck> {
    private static final String CODE = "INVALID_DISCOUNT_DEFINITION";
    private static final Set<InvoiceType> PROMOTABLE_INVOICE_TYPES = Set.of(InvoiceType.MONTHLY_INSTALLMENT, InvoiceType.ENROLLMENT_FEE, InvoiceType.RETAKEN_SUBJECT_FEE);

    @Override
    public String ruleName() {
        return "CohortPromotionTermsPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(DiscountDefinitionCheck check) {
        DiscountDefinitionDraft draft = check.draft();
        if (draft.discountType() != DiscountDefinitionType.COHORT_PROMOTION) {
            return PolicyVerdict.allow(ruleName());
        }
        if (draft.enrollmentWindowFrom() == null || draft.enrollmentWindowTo() == null) {
            return PolicyVerdict.deny(ruleName(), CODE, "La promoción de cohorte necesita la ventana de inscripción completa, con su inicio y su fin.");
        }
        if (draft.applicableInvoiceType() != null && draft.applicableInvoiceType() != InvoiceType.LATE_FEE && !PROMOTABLE_INVOICE_TYPES.contains(draft.applicableInvoiceType())) {
            return PolicyVerdict.deny(ruleName(), CODE, "La promoción de cohorte solo aplica a la cuota mensual, la matrícula o la materia recursada.");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
