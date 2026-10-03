package com.red_escuela.backend.announcement.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.time.LocalDateTime;

public class ScheduledDateInThePastException extends BadRequestException {

    public ScheduledDateInThePastException(LocalDateTime scheduledFor) {
        super("La fecha de envío programada ya pasó: " + scheduledFor);
    }
}
