package com.red_escuela.backend.shared.domain;

public class CurrencyMismatchException extends IllegalArgumentException {

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("Expected currency " + expected + " but got " + actual);
    }
}
