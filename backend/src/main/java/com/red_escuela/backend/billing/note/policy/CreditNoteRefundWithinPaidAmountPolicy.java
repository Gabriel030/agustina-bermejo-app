package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.note.domain.CreditNoteDestination;
import com.red_escuela.backend.billing.note.domain.CreditNoteLineAmount;

import java.math.BigDecimal;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CREDIT_NOTE_REFUND_EXCEEDS_PAID_AMOUNT"}, decisions = {"D-165"})
public final class CreditNoteRefundWithinPaidAmountPolicy implements Policy<CreditNoteIssuingContext> {

    @Override
    public String ruleName() {
        return "CreditNoteRefundWithinPaidAmountPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CreditNoteIssuingContext context) {
        if (context.invoice().currentDestination() != CreditNoteDestination.FREE_CREDIT) {
            return PolicyVerdict.allow(ruleName());
        }
        BigDecimal requested = totalRequested(context);
        BigDecimal refundable = context.invoice().paidAmount().value().amount().subtract(context.invoice().freeCreditedAmount().value().amount());
        if (requested.compareTo(refundable) > 0) {
            return PolicyVerdict.deny(ruleName(), "CREDIT_NOTE_REFUND_EXCEEDS_PAID_AMOUNT", "El crédito libre supera lo que se pagó de la factura");
        }
        return PolicyVerdict.allow(ruleName());
    }

    private BigDecimal totalRequested(CreditNoteIssuingContext context) {
        return context.command().lines().stream().map(line -> line.amount().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
