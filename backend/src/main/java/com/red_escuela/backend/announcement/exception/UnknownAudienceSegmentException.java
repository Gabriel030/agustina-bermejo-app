package com.red_escuela.backend.announcement.exception;

import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;

public class UnknownAudienceSegmentException extends RuntimeException {

    public UnknownAudienceSegmentException(AudienceSegmentKey key) {
        super("No resolver for audience segment " + key);
    }
}
