package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.dataimport.entity.ImportRowError;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ImportRowReader {
    private final Map<String, String> payload;
    private final List<ImportRowError> errors = new ArrayList<>();

    public ImportRowReader(Map<String, String> payload) {
        this.payload = payload;
    }

    public List<ImportRowError> errors() {
        return errors;
    }

    public String text(String column) {
        return ImportValueParser.cleanText(payload.get(column));
    }

    public String requiredText(String column, int maxLength) {
        String value = text(column);
        if (value.isEmpty()) {
            errors.add(new ImportRowError(column, code(column, "REQUIRED"), "La columna " + column + " es obligatoria"));
        } else if (value.length() > maxLength) {
            errors.add(new ImportRowError(column, code(column, "TOO_LONG"), "El texto supera " + maxLength + " caracteres"));
        }
        return value;
    }

    public Integer integer(String column, boolean required, int min, int max) {
        String value = text(column);
        if (value.isEmpty()) {
            if (required) {
                errors.add(new ImportRowError(column, code(column, "REQUIRED"), "La columna " + column + " es obligatoria"));
            }
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                errors.add(new ImportRowError(column, code(column, "OUT_OF_RANGE"), "El valor debe estar entre " + min + " y " + max));
                return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            errors.add(new ImportRowError(column, code(column, "INVALID"), "El valor debe ser un número entero"));
            return null;
        }
    }

    public BigDecimal decimal(String column, boolean required) {
        String value = text(column).replace(',', '.');
        if (value.isEmpty()) {
            if (required) {
                errors.add(new ImportRowError(column, code(column, "REQUIRED"), "La columna " + column + " es obligatoria"));
            }
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            errors.add(new ImportRowError(column, code(column, "INVALID"), "El valor debe ser un número sin símbolos"));
            return null;
        }
    }

    public java.time.LocalDate date(String column, boolean required) {
        String value = text(column);
        if (value.isEmpty()) {
            if (required) {
                errors.add(new ImportRowError(column, code(column, "REQUIRED"), "La columna " + column + " es obligatoria"));
            }
            return null;
        }
        try {
            return ImportValueParser.parseDate(value).orElse(null);
        } catch (IllegalArgumentException e) {
            errors.add(new ImportRowError(column, code(column, "INVALID"), "La fecha debe ser yyyy-MM-dd o dd/MM/yyyy"));
            return null;
        }
    }

    public <E extends Enum<E>> Optional<E> enumValue(String column, Class<E> type, boolean required) {
        String value = text(column);
        if (value.isEmpty()) {
            if (required) {
                errors.add(new ImportRowError(column, code(column, "REQUIRED"), "La columna " + column + " es obligatoria"));
            }
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(type, value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            errors.add(new ImportRowError(column, code(column, "INVALID"), "Valor no permitido en " + column));
            return Optional.empty();
        }
    }

    private static String code(String column, String suffix) {
        return column.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT) + "_" + suffix;
    }
}
