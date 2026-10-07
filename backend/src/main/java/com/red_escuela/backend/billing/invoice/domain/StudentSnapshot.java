package com.red_escuela.backend.billing.invoice.domain;

public record StudentSnapshot(String fullName, String documentNumber, String address) {

    public StudentSnapshot {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("fullName must not be blank");
        }
        if (documentNumber == null || documentNumber.isBlank()) {
            throw new IllegalArgumentException("documentNumber must not be blank");
        }
    }
}
