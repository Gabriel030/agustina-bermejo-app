package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CASH_PAYMENT_PREVIEW_OUTDATED"}, decisions = {"D-319", "D-207"})
public final class CashPaymentPreviewIsCurrentPolicy implements Policy<CashPaymentPreviewCheck> {

    @Override
    public String ruleName() {
        return "CashPaymentPreviewIsCurrentPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CashPaymentPreviewCheck check) {
        boolean current = check.submittedFingerprint() != null && check.submittedFingerprint().equals(check.currentFingerprint());
        return current
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "CASH_PAYMENT_PREVIEW_OUTDATED", "La cuenta cambió desde la vista previa: confirmá de nuevo con la vista previa actualizada.");
    }
}
