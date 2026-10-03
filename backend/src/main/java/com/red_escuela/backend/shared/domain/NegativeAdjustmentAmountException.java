package com.red_escuela.backend.shared.domain;

public class NegativeAdjustmentAmountException extends IllegalArgumentException {

    public NegativeAdjustmentAmountException() {
        super("Nominal adjustment amount must not be negative");
    }
}
