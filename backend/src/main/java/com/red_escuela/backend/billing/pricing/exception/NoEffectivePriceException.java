package com.red_escuela.backend.billing.pricing.exception;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.exception.ConflictException;

import java.time.LocalDate;

public class NoEffectivePriceException extends ConflictException {
    public NoEffectivePriceException(FeeType feeType, LocalDate date) {
        super("No hay un precio vigente para " + feeType + " al " + date + ".");
    }
}
