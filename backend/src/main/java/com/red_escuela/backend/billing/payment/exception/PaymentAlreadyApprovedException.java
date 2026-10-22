package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class PaymentAlreadyApprovedException extends BankTransferReceiptConflictException {

    public static PaymentAlreadyApprovedException concurrentApproval() {
        return new PaymentAlreadyApprovedException();
    }

    private PaymentAlreadyApprovedException() {
        super("The payment was already approved and credited to the account",
                List.of(new PolicyViolation("PAYMENT_ALREADY_APPROVED", "El comprobante ya fue aprobado por otra persona.")));
    }
}
