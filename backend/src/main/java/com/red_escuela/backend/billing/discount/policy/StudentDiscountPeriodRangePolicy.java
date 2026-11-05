package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"INVALID_SCHOLARSHIP_PERIOD"}, decisions = {"D-282", "D-384"})
public final class StudentDiscountPeriodRangePolicy implements Policy<StudentDiscountGrantCheck> {

    @Override
    public String ruleName() {
        return "StudentDiscountPeriodRangePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(StudentDiscountGrantCheck check) {
        if (check.from() == null || check.to() == null) {
            return deny("Indicá desde qué cuota y hasta qué cuota rige.");
        }
        if (check.to().isBefore(check.from())) {
            return deny("La cuota final no puede ser anterior a la inicial.");
        }
        boolean singleChargeSpansMonths = check.invoiceType() != InvoiceType.MONTHLY_INSTALLMENT && !check.to().equals(check.from());
        return singleChargeSpansMonths
                ? deny("Un descuento sobre un cargo que no es la cuota mensual rige en un solo período.")
                : PolicyVerdict.allow(ruleName());
    }

    private PolicyVerdict deny(String message) {
        return PolicyVerdict.deny(ruleName(), "INVALID_SCHOLARSHIP_PERIOD", message);
    }
}
