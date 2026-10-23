package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class PaymentNotFoundException extends NotFoundException implements ReportsErrorCode {

    public PaymentNotFoundException() {
        super("The payment does not exist");
    }
}
