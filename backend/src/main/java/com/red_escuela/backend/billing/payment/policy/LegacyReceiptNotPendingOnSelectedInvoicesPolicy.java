package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"LEGACY_RECEIPT_PENDING_ON_INVOICE"}, decisions = {"D-260"})
public final class LegacyReceiptNotPendingOnSelectedInvoicesPolicy implements Policy<BankTransferReceiptRegistrationCheck> {

    @Override
    public String ruleName() {
        return "LegacyReceiptNotPendingOnSelectedInvoicesPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(BankTransferReceiptRegistrationCheck check) {
        return check.legacyReceiptPendingOnSelectedInvoice()
                ? PolicyVerdict.deny(ruleName(), "LEGACY_RECEIPT_PENDING_ON_INVOICE",
                "Una de las facturas elegidas ya tiene un comprobante pendiente de revisión. Esperá a que se resuelva antes de subir otro.")
                : PolicyVerdict.allow(ruleName());
    }
}
