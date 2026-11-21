package com.red_escuela.backend.academicrecord.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class CalendarFeedNotFoundException extends NotFoundException {

    public CalendarFeedNotFoundException() {
        super("Calendar feed not found");
    }
}
