package com.red_escuela.backend.billing.invoice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public record InvoiceNumber(@Column(name = "invoice_number", length = 20) String value) {

    private static final Pattern FORMAT = Pattern.compile("(F|ND)-\\d{8}");
    private static final String INVOICE_PREFIX = "F-";
    private static final String DEBIT_NOTE_PREFIX = "ND-";
    private static final long MAX_SEQUENCE_VALUE = 99_999_999L;

    public InvoiceNumber {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid invoice number " + value);
        }
    }

    public static InvoiceNumber formatInvoiceNumber(InvoiceDocumentType documentType, long sequenceValue) {
        if (sequenceValue < 1 || sequenceValue > MAX_SEQUENCE_VALUE) {
            throw new IllegalArgumentException("Invoice number sequence out of range: " + sequenceValue);
        }
        return new InvoiceNumber(prefixOf(documentType) + String.format("%08d", sequenceValue));
    }

    public boolean belongsTo(InvoiceDocumentType documentType) {
        return value.startsWith(prefixOf(documentType));
    }

    private static String prefixOf(InvoiceDocumentType documentType) {
        Objects.requireNonNull(documentType, "documentType");
        return documentType == InvoiceDocumentType.INVOICE ? INVOICE_PREFIX : DEBIT_NOTE_PREFIX;
    }
}
