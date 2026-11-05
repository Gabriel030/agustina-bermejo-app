package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class PaymentReceiptAlreadyReviewedException extends BankTransferReceiptConflictException {

    public PaymentReceiptAlreadyReviewedException() {
        super("The payment receipt was already reviewed by someone else",
                List.of(new PolicyViolation("PAYMENT_RECEIPT_ALREADY_REVIEWED", "El comprobante ya fue revisado por otra persona.")));
    }
}
