package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.exception.DataIntegrityBrokenException;

public class PaymentSourceIntegrityException extends DataIntegrityBrokenException {
    public PaymentSourceIntegrityException(Long paymentId, String problem) {
        super("Payment " + paymentId + " referenced by an allocation source " + problem);
    }

    public static PaymentSourceIntegrityException missing(Long paymentId) {
        return new PaymentSourceIntegrityException(paymentId, "does not exist");
    }

    public static PaymentSourceIntegrityException ofAnotherAccount(Long paymentId) {
        return new PaymentSourceIntegrityException(paymentId, "belongs to another account");
    }
}
