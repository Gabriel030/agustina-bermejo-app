package com.red_escuela.backend.dataimport.specialist;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;

public final class ImportValueParser {
    private static final DateTimeFormatter DAY_FIRST = DateTimeFormatter.ofPattern("d/M/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    private ImportValueParser() {
    }

    public static String cleanText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.strip().replaceAll("\\s+", " ");
    }

    public static String cleanDocumentNumber(String raw) {
        return cleanText(raw).replace(".", "").replace(" ", "");
    }

    public static Optional<LocalDate> parseDate(String raw) {
        String value = cleanText(raw);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (value.contains("/")) {
                return Optional.of(LocalDate.parse(value, DAY_FIRST));
            }
            return Optional.of(LocalDate.parse(value));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(value);
        }
    }
}
