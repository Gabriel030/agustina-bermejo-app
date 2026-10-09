package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.billing.payment.domain.PaymentEvent;
import com.red_escuela.backend.billing.payment.domain.PaymentStatus;
import com.red_escuela.backend.shared.exception.ConflictException;

public class IllegalPaymentStatusTransitionException extends ConflictException {

    public IllegalPaymentStatusTransitionException(PaymentStatus fromStatus, PaymentEvent event) {
        super("Event " + event + " is not allowed from payment status " + fromStatus);
    }
}
