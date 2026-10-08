package com.red_escuela.backend.billing.invoice.policy.issuing;

import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ENTRY_DISCOUNT_NOT_APPLICABLE", decisions = {"D-018", "D-019"})
public final class CohortEntryDiscountAppliesPolicy implements Policy<CohortEntryDiscountAppliesPolicy.Offer> {

    public record Offer(boolean entryEnrollmentFee, InvoiceIssuingReason issuingReason, Percentage discount) {
    }

    @Override
    public String ruleName() {
        return "CohortEntryDiscountAppliesPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(Offer offer) {
        boolean applies = offer.entryEnrollmentFee() && offer.issuingReason() != InvoiceIssuingReason.PROGRAM_ADMISSION
                && offer.discount() != null && offer.discount().fraction().signum() > 0;
        return applies ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "ENTRY_DISCOUNT_NOT_APPLICABLE", "No corresponde descuento de ingreso");
    }
}
