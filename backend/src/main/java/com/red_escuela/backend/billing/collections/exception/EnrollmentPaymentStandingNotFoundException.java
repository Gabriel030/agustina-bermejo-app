package com.red_escuela.backend.billing.collections.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class EnrollmentPaymentStandingNotFoundException extends NotFoundException implements ReportsErrorCode {

    public EnrollmentPaymentStandingNotFoundException() {
        super("The enrollment has no payment standing yet");
    }
}
