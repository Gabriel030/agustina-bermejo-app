package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptLimits;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"BANK_TRANSFER_AMOUNT_INVALID", "BANK_TRANSFER_DATE_REQUIRED", "BANK_TRANSFER_DATE_IN_FUTURE",
        "BANK_TRANSFER_OPERATION_NUMBER_REQUIRED", "BANK_TRANSFER_OPERATION_NUMBER_TOO_LONG", "BANK_TRANSFER_ORIGIN_ACCOUNT_REQUIRED",
        "BANK_TRANSFER_ORIGIN_ACCOUNT_TOO_LONG", "BANK_TRANSFER_HOLDER_NAME_REQUIRED", "BANK_TRANSFER_HOLDER_NAME_TOO_LONG",
        "BANK_TRANSFER_BANK_NAME_TOO_LONG", "BANK_TRANSFER_RECEIPT_FILE_REQUIRED", "BANK_TRANSFER_INVOICES_REQUIRED"},
        decisions = {"D-267", "D-083", "D-097", "D-320"})
public final class BankTransferReceiptDetailsRequiredPolicy implements Policy<BankTransferReceiptDetailsCheck> {

    @Override
    public String ruleName() {
        return "BankTransferReceiptDetailsRequiredPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(BankTransferReceiptDetailsCheck check) {
        if (check.declaredAmount() == null || BankTransferReceiptLimits.isNotPositive(check.declaredAmount())
                || BankTransferReceiptLimits.exceedsStorableAmount(check.declaredAmount())) {
            return deny("BANK_TRANSFER_AMOUNT_INVALID", "El monto de la transferencia es obligatorio y mayor que cero.");
        }
        if (check.transferDate() == null) {
            return deny("BANK_TRANSFER_DATE_REQUIRED", "La fecha de la transferencia es obligatoria.");
        }
        if (check.transferDate().isAfter(check.today())) {
            return deny("BANK_TRANSFER_DATE_IN_FUTURE", "La fecha de la transferencia no puede ser futura.");
        }
        PolicyVerdict operationNumber = textField(check.operationNumber(), BankTransferReceiptLimits.MAX_OPERATION_NUMBER_LENGTH,
                "BANK_TRANSFER_OPERATION_NUMBER_REQUIRED", "BANK_TRANSFER_OPERATION_NUMBER_TOO_LONG", "El número de operación");
        if (!operationNumber.allowed()) {
            return operationNumber;
        }
        PolicyVerdict originAccount = textField(check.originAccount(), BankTransferReceiptLimits.MAX_ORIGIN_ACCOUNT_LENGTH,
                "BANK_TRANSFER_ORIGIN_ACCOUNT_REQUIRED", "BANK_TRANSFER_ORIGIN_ACCOUNT_TOO_LONG", "El CBU o alias de origen");
        if (!originAccount.allowed()) {
            return originAccount;
        }
        PolicyVerdict holderName = textField(check.holderName(), BankTransferReceiptLimits.MAX_HOLDER_NAME_LENGTH,
                "BANK_TRANSFER_HOLDER_NAME_REQUIRED", "BANK_TRANSFER_HOLDER_NAME_TOO_LONG", "El titular de la cuenta");
        if (!holderName.allowed()) {
            return holderName;
        }
        if (check.bankName() != null && check.bankName().strip().length() > BankTransferReceiptLimits.MAX_BANK_NAME_LENGTH) {
            return deny("BANK_TRANSFER_BANK_NAME_TOO_LONG", "El nombre del banco tiene hasta " + BankTransferReceiptLimits.MAX_BANK_NAME_LENGTH + " caracteres.");
        }
        if (!check.hasFile()) {
            return deny("BANK_TRANSFER_RECEIPT_FILE_REQUIRED", "El archivo del comprobante es obligatorio.");
        }
        if (check.selectedInvoiceCount() < 1) {
            return deny("BANK_TRANSFER_INVOICES_REQUIRED", "Elegí al menos una factura.");
        }
        return PolicyVerdict.allow(ruleName());
    }

    private PolicyVerdict textField(String value, int maxLength, String requiredCode, String tooLongCode, String label) {
        if (value == null || value.isBlank()) {
            return deny(requiredCode, label + " es obligatorio.");
        }
        if (value.strip().length() > maxLength) {
            return deny(tooLongCode, label + " tiene hasta " + maxLength + " caracteres.");
        }
        return PolicyVerdict.allow(ruleName());
    }

    private PolicyVerdict deny(String code, String reason) {
        return PolicyVerdict.deny(ruleName(), code, reason);
    }
}
