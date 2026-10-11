package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "DEBIT_NOTE_RECIPIENT_MISMATCH", decisions = {"D-244", "D-097"})
public final class DebitNoteSameRecipientAsOriginPolicy implements Policy<LateFeeIssuingContext> {

    @Override
    public String ruleName() {
        return "DebitNoteSameRecipientAsOriginPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(LateFeeIssuingContext context) {
        LateFeeOriginFacts origin = context.origin();
        LateFeeDebitNoteDraftFacts draft = context.draft();
        boolean sameRecipient = origin.studentCurrentAccountId().equals(draft.studentCurrentAccountId())
                && origin.currency() == draft.currency()
                && origin.recipient().equals(draft.recipient());
        return sameRecipient ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "DEBIT_NOTE_RECIPIENT_MISMATCH", "La nota de débito debe ir al mismo receptor, cuenta y moneda que la factura de origen");
    }
}
