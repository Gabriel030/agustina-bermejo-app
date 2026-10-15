package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CURRENCY_MISMATCH"}, decisions = {"D-097"})
public final class DiscountCurrencyMatchesOfferingPolicy implements Policy<DiscountDefinitionCheck> {

    @Override
    public String ruleName() {
        return "DiscountCurrencyMatchesOfferingPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(DiscountDefinitionCheck check) {
        boolean mismatch = check.findOfferingCurrency().map(currency -> hasAmountInAnotherCurrency(check.draft(), currency)).orElse(false);
        return mismatch
                ? PolicyVerdict.deny(ruleName(), "CURRENCY_MISMATCH", "El monto fijo y el tope deben estar en la moneda de la oferta.")
                : PolicyVerdict.allow(ruleName());
    }

    private boolean hasAmountInAnotherCurrency(DiscountDefinitionDraft draft, Currency offeringCurrency) {
        boolean fixedAmountMismatch = draft.calculation() instanceof MonetaryAdjustment.Nominal nominal && nominal.amount().currency() != offeringCurrency;
        boolean capMismatch = draft.capPerInvoice() != null && draft.capPerInvoice().currency() != offeringCurrency;
        return fixedAmountMismatch || capMismatch;
    }
}
