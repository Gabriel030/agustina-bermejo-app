package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"POSSIBLE_DUPLICATE_BANK_TRANSFER"}, decisions = {"D-269"})
public final class DuplicateBankTransferPolicy implements Policy<BankTransferReceiptRegistrationCheck> {

    @Override
    public String ruleName() {
        return "DuplicateBankTransferPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(BankTransferReceiptRegistrationCheck check) {
        return check.duplicateTransferExists()
                ? PolicyVerdict.deny(ruleName(), "POSSIBLE_DUPLICATE_BANK_TRANSFER", "Ya hay un comprobante con el mismo número de operación, fecha y monto.")
                : PolicyVerdict.allow(ruleName());
    }
}
