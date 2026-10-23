package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.billing.payment.domain.RefundEvent;
import com.red_escuela.backend.billing.payment.domain.RefundStatus;
import com.red_escuela.backend.shared.exception.ConflictException;

public class IllegalRefundStatusTransitionException extends ConflictException {

    public IllegalRefundStatusTransitionException(RefundStatus fromStatus, RefundEvent event) {
        super("Event " + event + " is not allowed from refund status " + fromStatus);
    }
}
