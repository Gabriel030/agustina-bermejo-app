package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"SCHOLARSHIP_START_BEFORE_NEXT_INSTALLMENT"}, decisions = {"D-238"})
public final class ScholarshipStartsAtNextInstallmentPolicy implements Policy<StudentDiscountGrantCheck> {

    @Override
    public String ruleName() {
        return "ScholarshipStartsAtNextInstallmentPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(StudentDiscountGrantCheck check) {
        boolean startsBeforeNextInstallment = check.invoiceType() == InvoiceType.MONTHLY_INSTALLMENT && check.from() != null
                && check.from().isBefore(check.nextInstallmentPeriod());
        return startsBeforeNextInstallment
                ? PolicyVerdict.deny(ruleName(), "SCHOLARSHIP_START_BEFORE_NEXT_INSTALLMENT",
                        "El descuento no puede empezar antes de la próxima cuota a emitir (" + check.nextInstallmentPeriod() + ").")
                : PolicyVerdict.allow(ruleName());
    }
}
