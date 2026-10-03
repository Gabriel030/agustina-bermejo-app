package com.red_escuela.backend.exception;

import java.util.Locale;

public record ErrorCodeResponse(String code) {
    private static final String EXCEPTION_SUFFIX = "Exception";

    public static ErrorCodeResponse of(Throwable exception) {
        String name = exception.getClass().getSimpleName();
        if (name.endsWith(EXCEPTION_SUFFIX)) {
            name = name.substring(0, name.length() - EXCEPTION_SUFFIX.length());
        }
        return new ErrorCodeResponse(name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT));
    }
}
