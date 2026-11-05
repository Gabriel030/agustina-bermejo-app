package com.red_escuela.backend.billing.discount.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class DiscountScopeOfferingNotFoundException extends NotFoundException {
    public DiscountScopeOfferingNotFoundException(String offeringKind, Long offeringId) {
        super("No existe " + offeringKind + " " + offeringId + " para acotar la promoción.");
    }
}
