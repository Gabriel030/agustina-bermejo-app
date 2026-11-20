package com.red_escuela.backend.notification.broadcast.draft.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class BroadcastNotFoundException extends NotFoundException implements ReportsErrorCode {

    public BroadcastNotFoundException(Long id) {
        super("Broadcast " + id + " not found");
    }
}
