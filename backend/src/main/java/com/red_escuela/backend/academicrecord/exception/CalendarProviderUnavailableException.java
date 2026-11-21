package com.red_escuela.backend.academicrecord.exception;

import com.red_escuela.backend.shared.exception.CircuitOpenException;

import java.time.Duration;

public class CalendarProviderUnavailableException extends CircuitOpenException {

    public CalendarProviderUnavailableException() {
        super("calendar-events-provider", Duration.ofMinutes(5));
    }
}
