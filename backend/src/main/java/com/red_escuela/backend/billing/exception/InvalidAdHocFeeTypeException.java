package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.billing.entity.FeeType;

public class InvalidAdHocFeeTypeException extends RuntimeException {
    public InvalidAdHocFeeTypeException(FeeType feeType) {
        super("FeeType inválido para un concepto ad-hoc: " + feeType);
    }
}
