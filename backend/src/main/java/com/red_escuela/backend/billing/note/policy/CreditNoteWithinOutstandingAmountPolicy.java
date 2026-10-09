package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.note.domain.CreditNoteDestination;
import com.red_escuela.backend.billing.note.domain.CreditNoteLineAmount;

import java.math.BigDecimal;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CREDIT_NOTE_EXCEEDS_CREDITABLE_AMOUNT"}, decisions = {"D-165"})
public final class CreditNoteWithinOutstandingAmountPolicy implements Policy<CreditNoteIssuingContext> {

    @Override
    public String ruleName() {
        return "CreditNoteWithinOutstandingAmountPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CreditNoteIssuingContext context) {
        if (context.invoice().currentDestination() != CreditNoteDestination.DEBT_OFFSET) {
            return PolicyVerdict.allow(ruleName());
        }
        BigDecimal requested = context.command().lines().stream().map(CreditNoteLineAmount::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (requested.compareTo(context.invoice().outstandingAmount().value().amount()) > 0) {
            return PolicyVerdict.deny(ruleName(), "CREDIT_NOTE_EXCEEDS_CREDITABLE_AMOUNT", "La nota de crédito supera lo que la factura tiene impago");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
