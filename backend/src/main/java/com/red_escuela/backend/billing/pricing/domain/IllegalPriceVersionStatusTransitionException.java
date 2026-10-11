package com.red_escuela.backend.billing.pricing.domain;

import com.red_escuela.backend.shared.exception.ConflictException;

public class IllegalPriceVersionStatusTransitionException extends ConflictException {
    public IllegalPriceVersionStatusTransitionException(PriceVersionStatus from, PriceVersionEvent event) {
        super("Price version in status " + from + " does not accept " + event);
    }
}
