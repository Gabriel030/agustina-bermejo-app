package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"DISCOUNT_INVOICE_TYPE_MISMATCH"}, decisions = {"D-129", "D-157", "D-384"})
public final class StudentDiscountInvoiceTypePolicy implements Policy<StudentDiscountGrantCheck> {

    @Override
    public String ruleName() {
        return "StudentDiscountInvoiceTypePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(StudentDiscountGrantCheck check) {
        if (check.invoiceType() == InvoiceType.LATE_FEE) {
            return PolicyVerdict.deny(ruleName(), "DISCOUNT_INVOICE_TYPE_MISMATCH", "Un descuento nunca se aplica a un recargo.");
        }
        boolean scholarshipOutsideInstallments = check.discountType() == DiscountType.SCHOLARSHIP && check.invoiceType() != InvoiceType.MONTHLY_INSTALLMENT;
        return scholarshipOutsideInstallments
                ? PolicyVerdict.deny(ruleName(), "DISCOUNT_INVOICE_TYPE_MISMATCH", "La beca solo aplica a cuotas mensuales; para bonificar otra factura, otorgá un descuento individual.")
                : PolicyVerdict.allow(ruleName());
    }
}
