package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class PossibleDuplicateBankTransferException extends BankTransferReceiptConflictException {

    public PossibleDuplicateBankTransferException(List<PolicyViolation> violations) {
        super("A payment with the same operation number, date and amount already exists", violations);
    }
}
