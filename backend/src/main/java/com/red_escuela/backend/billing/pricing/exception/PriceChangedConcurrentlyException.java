package com.red_escuela.backend.billing.pricing.exception;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.exception.ConflictException;

public class PriceChangedConcurrentlyException extends ConflictException {
    public PriceChangedConcurrentlyException(FeeType feeType, Throwable cause) {
        super("Otro cambio de precio de " + feeType + " se confirmó al mismo tiempo. Reintentá con el precio actualizado.", cause);
    }
}
