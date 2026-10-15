package com.red_escuela.backend.shared.domain;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class UnsupportedCurrencyException extends BadRequestException {

    public UnsupportedCurrencyException(String currency) {
        super("Unsupported currency " + currency);
    }
}
