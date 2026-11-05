package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"INVALID_STUDENT_DISCOUNT"}, decisions = {"D-075", "D-384"})
public final class StudentDiscountCoherencePolicy implements Policy<StudentDiscountGrantCheck> {
    public static final int MAX_REASON_LENGTH = 400;

    @Override
    public String ruleName() {
        return "StudentDiscountCoherencePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(StudentDiscountGrantCheck check) {
        if (check.discountType() != DiscountType.SCHOLARSHIP && check.discountType() != DiscountType.INDIVIDUAL) {
            return deny("Solo se otorgan becas y descuentos individuales.");
        }
        if (check.calculation() == null || isZero(check.calculation())) {
            return deny("El porcentaje o el monto debe ser mayor a cero.");
        }
        if (check.capPerInvoice() != null && check.capPerInvoice().value().isZero()) {
            return deny("El tope por factura debe ser mayor a cero.");
        }
        boolean reasonInvalid = check.reason() == null || check.reason().isBlank() || check.reason().strip().length() > MAX_REASON_LENGTH;
        return reasonInvalid ? deny("El motivo es obligatorio y admite hasta " + MAX_REASON_LENGTH + " caracteres.") : PolicyVerdict.allow(ruleName());
    }

    private boolean isZero(MonetaryAdjustment calculation) {
        return switch (calculation) {
            case MonetaryAdjustment.Proportional proportional -> proportional.rate().fraction().signum() == 0;
            case MonetaryAdjustment.Nominal nominal -> nominal.amount().isZero();
        };
    }

    private PolicyVerdict deny(String message) {
        return PolicyVerdict.deny(ruleName(), "INVALID_STUDENT_DISCOUNT", message);
    }
}
