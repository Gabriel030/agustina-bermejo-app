package com.red_escuela.backend.shared.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public enum DocumentType {
    DNI("^\\d{7,8}$", "DNI"),
    PASSPORT("^[A-Za-z0-9]{4,20}$", "pasaporte"),
    OTHER("^[A-Za-z0-9]{4,20}$", "documento");

    private final Pattern format;
    private final String label;

    DocumentType(String format, String label) {
        this.format = Pattern.compile(format);
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean accepts(String number) {
        return format.matcher(number).matches();
    }

    public String normalize(String number) {
        return this == DNI ? number : number.toUpperCase(Locale.ROOT);
    }
}
