package com.red_escuela.backend.shared.validation;

import com.red_escuela.backend.shared.exception.RequestDateRangeInvalidException;
import com.red_escuela.backend.shared.exception.RequestFieldOutOfRangeException;
import com.red_escuela.backend.shared.exception.RequestFieldTooLongException;
import com.red_escuela.backend.shared.exception.RequestListTooLargeException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

public final class RequestLimits {

    public static final int SHORT_TEXT = 255;
    public static final int MAX_COLLECTION_SIZE = 100;

    private RequestLimits() {
    }

    public static void requireMaxLength(String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw new RequestFieldTooLongException("El campo " + field + " supera el máximo de " + max + " caracteres");
        }
    }

    public static void requireBetween(String field, Integer value, int min, int max) {
        if (value != null && (value < min || value > max)) {
            throw new RequestFieldOutOfRangeException("El campo " + field + " debe estar entre " + min + " y " + max);
        }
    }

    public static void requireNotAfter(String startField, LocalDateTime start, String endField, LocalDateTime end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new RequestDateRangeInvalidException("El campo " + startField + " no puede ser posterior a " + endField);
        }
    }

    public static void requireMaxSize(String field, Collection<?> values, int max) {
        if (values != null && values.size() > max) {
            throw new RequestListTooLargeException("El campo " + field + " supera el máximo de " + max + " elementos");
        }
    }

    public static void requireMaxSize(String field, Map<?, ?> values, int max) {
        if (values != null && values.size() > max) {
            throw new RequestListTooLargeException("El campo " + field + " supera el máximo de " + max + " elementos");
        }
    }
}
