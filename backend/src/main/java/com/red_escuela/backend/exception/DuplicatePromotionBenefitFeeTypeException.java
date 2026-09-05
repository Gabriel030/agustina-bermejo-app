package com.red_escuela.backend.exception;

public class DuplicatePromotionBenefitFeeTypeException extends RuntimeException {
    public DuplicatePromotionBenefitFeeTypeException(String feeType) {
        super("Ya hay un beneficio para el feeType " + feeType + " en esta promoción — no se pueden combinar dos benefits del mismo tipo");
    }
}
