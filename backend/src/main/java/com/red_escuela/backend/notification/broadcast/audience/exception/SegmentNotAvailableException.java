package com.red_escuela.backend.notification.broadcast.audience.exception;

import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentKey;
import com.red_escuela.backend.shared.exception.ConflictException;

public class SegmentNotAvailableException extends ConflictException {

    public SegmentNotAvailableException(SegmentKey key) {
        super("Segment " + key + " is not available");
    }
}
