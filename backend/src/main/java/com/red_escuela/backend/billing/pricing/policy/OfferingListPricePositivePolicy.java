package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "OFFERING_LIST_PRICE_NOT_POSITIVE", decisions = {"D-536"})
public final class OfferingListPricePositivePolicy implements Policy<PriceVersionDraft> {

    public String ruleName() {
        return "OfferingListPricePositivePolicy";
    }

    public PolicyVerdict evaluateRule(PriceVersionDraft draft) {
        boolean isOfferingListPrice = draft.feeType() == FeeType.MONTHLY_INSTALLMENT || draft.feeType() == FeeType.ENROLLMENT_FEE;
        return !isOfferingListPrice || draft.baseAmount().amount().signum() > 0 ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "OFFERING_LIST_PRICE_NOT_POSITIVE", "El precio de lista de la cuota y de la matrícula debe ser mayor que cero. Para ofrecerlo gratis, usá un descuento.");
    }
}
