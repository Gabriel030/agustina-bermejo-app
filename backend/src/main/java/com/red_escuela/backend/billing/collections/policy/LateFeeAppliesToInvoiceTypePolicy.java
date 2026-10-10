package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.Set;

@PolicyRule(violationCodes = "LATE_FEE_NOT_APPLICABLE", decisions = {"D-289", "D-242", "D-149", "D-155"})
public final class LateFeeAppliesToInvoiceTypePolicy implements Policy<LateFeeIssuingContext> {
    private static final Set<InvoiceType> TYPES_WITH_LATE_FEE = Set.of(InvoiceType.MONTHLY_INSTALLMENT, InvoiceType.ENROLLMENT_FEE);

    @Override
    public String ruleName() {
        return "LateFeeAppliesToInvoiceTypePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(LateFeeIssuingContext context) {
        boolean applies = context.origin().documentType() == InvoiceDocumentType.INVOICE && TYPES_WITH_LATE_FEE.contains(context.origin().invoiceType());
        return applies ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "LATE_FEE_NOT_APPLICABLE", "Solo las cuotas mensuales y las matrículas generan recargo");
    }
}
