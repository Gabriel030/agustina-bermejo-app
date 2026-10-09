package com.red_escuela.backend.shared.domain;

import java.util.Objects;

public record DocumentNumber(DocumentType type, String value) {

    public DocumentNumber {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        value = value.strip();
        if (!type.accepts(value)) {
            throw new InvalidDocumentNumberException(type);
        }
        value = type.normalize(value);
    }

    public static DocumentNumber of(DocumentType type, String value) {
        return new DocumentNumber(type, value);
    }
}
