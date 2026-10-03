package com.red_escuela.backend.shared.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record EmailAddress(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    public EmailAddress {
        Objects.requireNonNull(value, "value");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidEmailAddressException();
        }
    }

    public static EmailAddress of(String value) {
        return new EmailAddress(value);
    }
}
