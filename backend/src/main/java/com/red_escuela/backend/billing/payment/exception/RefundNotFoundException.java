package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class RefundNotFoundException extends NotFoundException implements ReportsErrorCode {

    public RefundNotFoundException() {
        super("The refund does not exist");
    }
}
