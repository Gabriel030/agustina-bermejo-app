package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"LEGACY_RECEIPT_HAS_NO_VERIFIABLE_PAYMENT"}, decisions = {"D-139"})
public final class LegacyReceiptNotVerifiablePolicy implements Policy<ReceiptApprovalCheck> {

    @Override
    public String ruleName() {
        return "LegacyReceiptNotVerifiablePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(ReceiptApprovalCheck check) {
        return check.legacyReceiptWithoutPayment() && check.verificationProvided()
                ? PolicyVerdict.deny(ruleName(), "LEGACY_RECEIPT_HAS_NO_VERIFIABLE_PAYMENT",
                "Este comprobante es anterior al nuevo circuito y no admite monto, fecha ni nota verificados.")
                : PolicyVerdict.allow(ruleName());
    }
}
