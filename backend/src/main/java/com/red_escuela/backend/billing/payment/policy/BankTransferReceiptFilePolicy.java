package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.ReceiptFileSignature;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"BANK_TRANSFER_RECEIPT_FILE_TYPE_NOT_ALLOWED", "BANK_TRANSFER_RECEIPT_FILE_CONTENT_MISMATCH"}, decisions = {"D-267"})
public final class BankTransferReceiptFilePolicy implements Policy<BankTransferReceiptFileCheck> {

    @Override
    public String ruleName() {
        return "BankTransferReceiptFilePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(BankTransferReceiptFileCheck check) {
        if (check.declaredContentType() == null || !ReceiptFileSignature.ALLOWED_CONTENT_TYPES.contains(check.declaredContentType())) {
            return PolicyVerdict.deny(ruleName(), "BANK_TRANSFER_RECEIPT_FILE_TYPE_NOT_ALLOWED", "El comprobante tiene que ser un PDF, JPG o PNG.");
        }
        if (!check.declaredContentType().equals(check.detectedContentType())) {
            return PolicyVerdict.deny(ruleName(), "BANK_TRANSFER_RECEIPT_FILE_CONTENT_MISMATCH", "El contenido del archivo no coincide con su tipo.");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
