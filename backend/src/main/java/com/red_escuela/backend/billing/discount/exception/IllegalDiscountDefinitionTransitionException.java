package com.red_escuela.backend.billing.discount.exception;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionEvent;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus;
import com.red_escuela.backend.shared.exception.ConflictException;

public class IllegalDiscountDefinitionTransitionException extends ConflictException {
    public IllegalDiscountDefinitionTransitionException(DiscountDefinitionStatus from, DiscountDefinitionEvent event) {
        super("Discount definition in status " + from + " does not accept " + event);
    }
}
