package com.red_escuela.backend.billing.note.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public record CreditNoteNumber(@Column(name = "credit_note_number", length = 20) String value) {

    private static final Pattern FORMAT = Pattern.compile("NC-\\d{8}");
    private static final long MAX_SEQUENCE_VALUE = 99_999_999L;

    public CreditNoteNumber {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid credit note number " + value);
        }
    }

    public static CreditNoteNumber formatCreditNoteNumber(long sequenceValue) {
        if (sequenceValue < 1 || sequenceValue > MAX_SEQUENCE_VALUE) {
            throw new IllegalArgumentException("Credit note number sequence out of range: " + sequenceValue);
        }
        return new CreditNoteNumber("NC-" + String.format("%08d", sequenceValue));
    }
}
