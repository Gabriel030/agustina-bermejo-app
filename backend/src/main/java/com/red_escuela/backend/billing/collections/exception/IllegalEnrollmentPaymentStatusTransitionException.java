package com.red_escuela.backend.billing.collections.exception;

import com.red_escuela.backend.billing.collections.domain.EnrollmentPaymentEvent;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.shared.exception.ConflictException;

public class IllegalEnrollmentPaymentStatusTransitionException extends ConflictException {

    public IllegalEnrollmentPaymentStatusTransitionException(EnrollmentPaymentStatus fromStatus, EnrollmentPaymentEvent event) {
        super("Event " + event + " is not allowed from enrollment payment status " + fromStatus);
    }
}
