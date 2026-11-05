package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class BankTransferDetailsRequiredException extends BankTransferReceiptRejectedException {

    public BankTransferDetailsRequiredException(List<PolicyViolation> violations) {
        super("The bank transfer receipt details are missing or invalid", violations);
    }

    public static BankTransferDetailsRequiredException currencyRequired() {
        return new BankTransferDetailsRequiredException(List.of(new PolicyViolation("BANK_TRANSFER_CURRENCY_REQUIRED", "La moneda de la transferencia es obligatoria.")));
    }
}
