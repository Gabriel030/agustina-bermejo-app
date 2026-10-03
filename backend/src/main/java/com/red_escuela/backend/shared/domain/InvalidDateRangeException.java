package com.red_escuela.backend.shared.domain;

import java.time.temporal.Temporal;

public class InvalidDateRangeException extends IllegalArgumentException {

    public InvalidDateRangeException(Temporal start, Temporal end) {
        super("Range end " + end + " is before start " + start);
    }
}
