package com.red_escuela.backend.announcement.exception;

import java.time.LocalDateTime;

public class ScheduledDateInThePastException extends RuntimeException {

    public ScheduledDateInThePastException(LocalDateTime scheduledFor) {
        super("La fecha de envío programada ya pasó: " + scheduledFor);
    }
}
