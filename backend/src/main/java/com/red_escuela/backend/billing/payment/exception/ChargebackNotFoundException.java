package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ChargebackNotFoundException extends NotFoundException implements ReportsErrorCode {

    public ChargebackNotFoundException() {
        super("The chargeback does not exist");
    }
}
