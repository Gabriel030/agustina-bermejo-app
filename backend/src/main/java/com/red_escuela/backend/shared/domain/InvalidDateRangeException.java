package com.red_escuela.backend.shared.domain;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.time.temporal.Temporal;

public class InvalidDateRangeException extends BadRequestException {

    public InvalidDateRangeException(Temporal start, Temporal end) {
        super("Range end " + end + " is before start " + start);
    }
}
