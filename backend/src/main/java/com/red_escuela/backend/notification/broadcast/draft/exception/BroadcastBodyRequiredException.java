package com.red_escuela.backend.notification.broadcast.draft.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class BroadcastBodyRequiredException extends BadRequestException {

    public BroadcastBodyRequiredException() {
        super("Broadcast body is required when no template is given");
    }
}
