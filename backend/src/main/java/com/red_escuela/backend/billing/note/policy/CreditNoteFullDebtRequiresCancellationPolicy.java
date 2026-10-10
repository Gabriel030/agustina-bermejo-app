package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.note.domain.CreditNoteDestination;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.math.BigDecimal;

@PolicyRule(violationCodes = {"CREDIT_NOTE_FULL_DEBT_REQUIRES_CANCELLATION"}, decisions = {"D-167"})
public final class CreditNoteFullDebtRequiresCancellationPolicy implements Policy<CreditNoteIssuingContext> {

    @Override
    public String ruleName() {
        return "CreditNoteFullDebtRequiresCancellationPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CreditNoteIssuingContext context) {
        if (context.command().reason() == CreditNoteReason.CANCELLATION || context.invoice().currentDestination() != CreditNoteDestination.DEBT_OFFSET) {
            return PolicyVerdict.allow(ruleName());
        }
        BigDecimal requested = context.command().lines().stream().map(line -> line.amount().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (requested.compareTo(context.invoice().outstandingAmount().value().amount()) == 0) {
            return PolicyVerdict.deny(ruleName(), "CREDIT_NOTE_FULL_DEBT_REQUIRES_CANCELLATION",
                    "Para anular toda la deuda de la factura se usa la cancelación, no una nota de crédito.");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
