package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"RECEIPT_TRANSFER_DATE_MISMATCH"}, decisions = {"D-320", "D-243"})
public final class ReceiptTransferDateMatchesUploadDatePolicy implements Policy<ReceiptApprovalCheck> {

    @Override
    public String ruleName() {
        return "ReceiptTransferDateMatchesUploadDatePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(ReceiptApprovalCheck check) {
        if (check.uploadDate() == null || check.verifiedTransferDate() == null || check.uploadDate().equals(check.verifiedTransferDate())) {
            return PolicyVerdict.allow(ruleName());
        }
        return PolicyVerdict.deny(ruleName(), "RECEIPT_TRANSFER_DATE_MISMATCH", "La fecha de la transferencia (" + check.verifiedTransferDate()
                + ") no coincide con la fecha en que se subió el comprobante (" + check.uploadDate() + "): el comprobante fue rechazado.");
    }
}
