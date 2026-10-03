package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class DuplicatePromotionBenefitFeeTypeException extends BadRequestException {
    public DuplicatePromotionBenefitFeeTypeException(String feeType) {
        super("Ya hay un beneficio para el feeType " + feeType + " en esta promoción — no se pueden combinar dos benefits del mismo tipo");
    }
}
