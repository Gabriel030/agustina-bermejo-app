package com.red_escuela.backend.shared.domain;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class CurrencyMismatchException extends BadRequestException {

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("Expected currency " + expected + " but got " + actual);
    }
}
