package com.red_escuela.backend.graduation.certificate.regular.entity;

public record ValidityDays(int value) {

    public static final int MIN = 1;
    public static final int MAX = 365;

    public ValidityDays {
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException("La vigencia debe estar entre " + MIN + " y " + MAX + " días");
        }
    }
}
