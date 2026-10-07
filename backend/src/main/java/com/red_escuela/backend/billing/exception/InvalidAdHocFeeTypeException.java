package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import com.red_escuela.backend.billing.invoice.domain.FeeType;

public class InvalidAdHocFeeTypeException extends BadRequestException {
    public InvalidAdHocFeeTypeException(FeeType feeType) {
        super("FeeType inválido para un concepto ad-hoc: " + feeType);
    }
}
