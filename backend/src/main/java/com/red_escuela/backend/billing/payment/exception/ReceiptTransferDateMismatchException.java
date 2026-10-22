package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class ReceiptTransferDateMismatchException extends BankTransferReceiptConflictException {

    public ReceiptTransferDateMismatchException(List<PolicyViolation> violations) {
        super("The verified transfer date differs from the receipt upload date: the receipt was rejected", violations);
    }
}
