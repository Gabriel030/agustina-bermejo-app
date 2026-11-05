package com.red_escuela.backend.billing.annualupfront.exception;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequestEvent;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequestStatus;
import com.red_escuela.backend.shared.exception.ConflictException;

public class IllegalAnnualUpfrontRequestTransitionException extends ConflictException {
    public IllegalAnnualUpfrontRequestTransitionException(AnnualUpfrontRequestStatus from, AnnualUpfrontRequestEvent event) {
        super("Annual upfront request in status " + from + " does not accept " + event);
    }
}
