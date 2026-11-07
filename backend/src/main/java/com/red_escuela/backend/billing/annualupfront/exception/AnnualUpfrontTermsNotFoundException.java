package com.red_escuela.backend.billing.annualupfront.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class AnnualUpfrontTermsNotFoundException extends NotFoundException {
    public AnnualUpfrontTermsNotFoundException(String message) {
        super(message);
    }
}
