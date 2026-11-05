package com.red_escuela.backend.billing.configuration.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class ReminderScheduleTargetNotFoundException extends NotFoundException {
    public ReminderScheduleTargetNotFoundException(Long targetId) {
        super("Reminder schedule target not found: " + targetId);
    }
}
