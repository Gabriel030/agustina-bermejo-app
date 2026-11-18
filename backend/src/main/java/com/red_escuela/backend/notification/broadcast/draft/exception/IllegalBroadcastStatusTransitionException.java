package com.red_escuela.backend.notification.broadcast.draft.exception;

import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastEvent;
import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.shared.exception.ConflictException;

public class IllegalBroadcastStatusTransitionException extends ConflictException {

    public IllegalBroadcastStatusTransitionException(BroadcastStatus from, BroadcastEvent event) {
        super("Broadcast in status " + from + " does not accept " + event);
    }
}
