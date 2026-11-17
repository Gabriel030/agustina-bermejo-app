package com.red_escuela.backend.notification.broadcast.audience.exception;

import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentKey;
import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidSegmentCriterionException extends BadRequestException {

    public InvalidSegmentCriterionException(SegmentKey key) {
        super("Invalid value for segment " + key);
    }
}
